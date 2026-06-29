package com.example.starter.domain.catalog

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.catalog.dto.CreateProductRequest
import com.example.starter.domain.catalog.dto.SellerProductResponse
import com.example.starter.domain.catalog.dto.UpdateProductRequest
import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.repository.CategoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.repository.SellerRepository
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
) {

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
        )
        request.options.forEach { o ->
            val option = ProductOption(name = o.name!!, sku = o.sku!!, additionalPrice = o.additionalPrice)
            option.assignInventory(Inventory(quantity = o.stockQuantity, reserved = 0))
            product.addOption(option)
        }
        productRepository.save(product)
        return SellerProductResponse.from(product)
    }

    @Transactional
    fun update(userId: Long, productId: Long, request: UpdateProductRequest): SellerProductResponse {
        val product = ownedProduct(userId, productId)
        product.name = request.name!!
        product.basePrice = request.basePrice!!
        product.description = request.description
        product.status = request.status!!
        product.category = findCategory(request.categoryId)
        return SellerProductResponse.from(product)
    }

    /** 옵션 재고를 절대값으로 설정한다. 이미 예약된 수량보다 적게는 내릴 수 없다. */
    @Transactional
    fun adjustStock(userId: Long, productId: Long, optionId: Long, quantity: Int): SellerProductResponse {
        val product = ownedProduct(userId, productId)
        val option = product.options.firstOrNull { it.id == optionId }
            ?: throw BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND)
        val inventory = option.inventory
            ?: throw BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND)
        if (quantity < inventory.reserved) {
            throw BusinessException(
                ErrorCode.INVALID_INPUT,
                "이미 예약된 ${inventory.reserved}개보다 적은 수량으로 설정할 수 없습니다.",
            )
        }
        inventory.quantity = quantity
        return SellerProductResponse.from(product)
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
