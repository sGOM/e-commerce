package com.example.starter.domain.catalog

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.catalog.dto.BulkStockRequest
import com.example.starter.domain.catalog.dto.BulkStockResponse
import com.example.starter.domain.catalog.dto.CreateProductRequest
import com.example.starter.domain.catalog.dto.SellerProductResponse
import com.example.starter.domain.catalog.dto.UpdateProductRequest
import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.event.InventoryRestockedEvent
import com.example.starter.domain.catalog.event.ProductPriceChangedEvent
import com.example.starter.domain.catalog.repository.CategoryRepository
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.repository.SellerRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 판매자 상품 관리. 본인 상점의 상품/옵션/재고만 등록·수정할 수 있다(소유권 격리).
 */
@Service
@Transactional(readOnly = true)
class SellerProductService(
    private val sellerRepository: SellerRepository,
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    /** 본인 상점 상품 목록 — 공개 검색과 달리 DRAFT/HIDDEN 도 포함한다. */
    // ponytail: 페이징 없음, 판매자당 상품이 수백 개를 넘으면 Pageable 로 전환
    fun getMyProducts(userId: Long): List<SellerProductResponse> =
        productRepository.findBySellerIdOrderByIdDesc(requireNotNull(activeSeller(userId).id))
            .map { SellerProductResponse.from(it) }

    @Transactional
    fun create(userId: Long, request: CreateProductRequest): SellerProductResponse {
        val seller = activeSeller(userId)
        val product = Product(
            seller = seller,
            name = request.name!!,
            basePrice = request.basePrice!!,
            category = findCategory(request.categoryId),
            description = request.description,
            status = request.status,
            dawnDeliveryEligible = request.dawnDeliveryEligible,
            imageUrl = request.imageUrl,
        )
        request.options.forEach { o ->
            val option = ProductOption(name = o.name!!, sku = o.sku!!, additionalPrice = o.additionalPrice)
            option.assignInventory(Inventory(quantity = o.stockQuantity, reserved = 0))
            product.addOption(option)
        }
        productRepository.save(product)
        return SellerProductResponse.from(product)
    }

    /**
     * 정가([Product.basePrice])가 바뀌면 [ProductPriceChangedEvent] 를 발행해 위시리스트 가격 인하
     * 알림을 트리거한다(`docs/planning/wishlist-price-alert.md` AC12). `adjustStock` 의
     * [InventoryRestockedEvent] 와 동일하게 트랜잭션 커밋 이후 비동기로만 처리되도록 구독측에
     * `@TransactionalEventListener(AFTER_COMMIT)` + `@Async` 를 위임한다.
     */
    @Transactional
    fun update(userId: Long, productId: Long, request: UpdateProductRequest): SellerProductResponse {
        val product = ownedProduct(userId, productId)
        val oldPrice = product.basePrice
        product.name = request.name!!
        product.basePrice = request.basePrice!!
        product.description = request.description
        product.status = request.status!!
        product.category = findCategory(request.categoryId)
        product.dawnDeliveryEligible = request.dawnDeliveryEligible
        product.imageUrl = request.imageUrl
        if (product.basePrice != oldPrice) {
            eventPublisher.publishEvent(ProductPriceChangedEvent(requireNotNull(product.id), oldPrice, product.basePrice))
        }
        return SellerProductResponse.from(product)
    }

    /**
     * 옵션 재고를 절대값으로 설정한다. 이미 예약된 수량보다 적게는 내릴 수 없다.
     *
     * 가용재고(quantity - reserved)가 0 → 1 이상으로 전이되면 [InventoryRestockedEvent] 를 발행해
     * 재입고 알림을 트리거한다(`docs/planning/restock-alert.md`). 이벤트는 이 트랜잭션 커밋 이후에만
     * 처리되도록 구독측에서 `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` 로 받는다 —
     * 재고 갱신 자체는 알림 발송과 무관하게 즉시 끝나야 하기 때문이다(감사로그 `@Async` 패턴과 동일 원칙).
     * 참고: https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
     */
    @Transactional
    fun adjustStock(userId: Long, productId: Long, optionId: Long, quantity: Int): SellerProductResponse {
        val product = ownedProduct(userId, productId)
        val option = product.options.firstOrNull { it.id == optionId }
            ?: throw BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND)
        setStock(option, quantity)
        return SellerProductResponse.from(product)
    }

    /**
     * SKU 목록으로 여러 옵션 재고를 한 번에 절대값 설정한다(ROADMAP 4.3 CSV 일괄 수정).
     * 모르는 SKU·남의 SKU·예약분 미만 수량이 하나라도 있으면 전부 거절한다(부분 반영 없음).
     */
    @Transactional
    fun bulkAdjustStock(userId: Long, request: BulkStockRequest): BulkStockResponse {
        val seller = activeSeller(userId)
        val quantities = request.items.associate { it.sku!!.trim() to it.quantity!! }
        val options = productOptionRepository.findBySkuIn(quantities.keys)
            .filter { it.product.seller.id == seller.id }
            .associateBy { it.sku }
        val unknown = quantities.keys - options.keys
        if (unknown.isNotEmpty()) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "등록되지 않은 SKU: ${unknown.joinToString(", ")}")
        }
        quantities.forEach { (sku, quantity) -> setStock(options.getValue(sku), quantity) }
        return BulkStockResponse(quantities.size)
    }

    private fun setStock(option: ProductOption, quantity: Int) {
        val inventory = option.inventory
            ?: throw BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND)
        if (quantity < inventory.reserved) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "'${option.sku}' 은(는) 이미 예약된 ${inventory.reserved}개보다 적은 수량으로 설정할 수 없습니다.",
            )
        }
        val wasSoldOut = inventory.available <= 0
        inventory.quantity = quantity
        if (wasSoldOut && inventory.available > 0) {
            eventPublisher.publishEvent(InventoryRestockedEvent(requireNotNull(option.id)))
        }
    }

    private fun findCategory(categoryId: Long?): Category? =
        categoryId?.let {
            categoryRepository.findById(it)
                .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }
        }

    private fun activeSeller(userId: Long): Seller {
        val seller = sellerRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)
        if (!seller.status.canSell) {
            throw BusinessException(ErrorCode.SELLER_NOT_FOUND, "영업 중인 상점이 아닙니다.")
        }
        return seller
    }

    private fun ownedProduct(userId: Long, productId: Long): Product {
        val seller = activeSeller(userId)
        val product = productRepository.findWithDetailById(productId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }
        // 본인 상점 상품만 — 아니면 존재를 숨겨 NOT_FOUND 로 응답
        if (product.seller.id != seller.id) {
            throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        }
        return product
    }
}
