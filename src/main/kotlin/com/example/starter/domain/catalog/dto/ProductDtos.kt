package com.example.starter.domain.catalog.dto

import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import java.math.BigDecimal
import java.time.Instant

/** 상품 검색 조건 (모두 선택적 → 동적 쿼리) */
data class ProductSearchCondition(
    val keyword: String? = null, // 상품명 부분 일치
    val categoryId: Long? = null, // 하위 카테고리 포함
    val sellerId: Long? = null,
    val minPrice: Long? = null, // basePrice 이상
    val maxPrice: Long? = null, // basePrice 이하
    val sort: ProductSort = ProductSort.LATEST,
)

/** 상품 목록 정렬. 동순위는 최신순(id desc)으로 고정해 페이지 간 순서가 흔들리지 않게 한다. */
enum class ProductSort { LATEST, PRICE_ASC, PRICE_DESC, RATING_DESC }

/** 상품 목록 항목 (요약) */
data class ProductSummaryResponse(
    val id: Long,
    val name: String,
    val basePrice: Long,
    val status: ProductStatus,
    val sellerId: Long,
    val storeName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val avgRating: BigDecimal,
    val reviewCount: Int,
    val dawnDeliveryEligible: Boolean,
) {
    companion object {
        fun from(product: Product) = ProductSummaryResponse(
            id = requireNotNull(product.id),
            name = product.name,
            basePrice = product.basePrice,
            status = product.status,
            sellerId = requireNotNull(product.seller.id),
            storeName = product.seller.storeName,
            categoryId = product.category?.id,
            categoryName = product.category?.name,
            avgRating = product.avgRating,
            reviewCount = product.reviewCount,
            dawnDeliveryEligible = product.dawnDeliveryEligible,
        )
    }
}

/** 인기 상품 응답 — 요약 정보 + 누적 판매 수량 */
data class PopularProductResponse(
    val id: Long,
    val name: String,
    val basePrice: Long,
    val status: ProductStatus,
    val sellerId: Long,
    val storeName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val avgRating: BigDecimal,
    val reviewCount: Int,
    val soldQuantity: Long,
    val dawnDeliveryEligible: Boolean,
) {
    companion object {
        fun from(product: Product, soldQuantity: Long) = PopularProductResponse(
            id = requireNotNull(product.id),
            name = product.name,
            basePrice = product.basePrice,
            status = product.status,
            sellerId = requireNotNull(product.seller.id),
            storeName = product.seller.storeName,
            categoryId = product.category?.id,
            categoryName = product.category?.name,
            avgRating = product.avgRating,
            reviewCount = product.reviewCount,
            soldQuantity = soldQuantity,
            dawnDeliveryEligible = product.dawnDeliveryEligible,
        )
    }
}

/** 상품 상세 (옵션·재고 포함) */
data class ProductDetailResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val basePrice: Long,
    val status: ProductStatus,
    val sellerId: Long,
    val storeName: String,
    val categoryId: Long?,
    val categoryName: String?,
    val avgRating: BigDecimal,
    val reviewCount: Int,
    val options: List<ProductOptionResponse>,
    val createdAt: Instant,
    val dawnDeliveryEligible: Boolean,
) {
    companion object {
        fun from(product: Product) = ProductDetailResponse(
            id = requireNotNull(product.id),
            name = product.name,
            description = product.description,
            basePrice = product.basePrice,
            status = product.status,
            sellerId = requireNotNull(product.seller.id),
            storeName = product.seller.storeName,
            categoryId = product.category?.id,
            categoryName = product.category?.name,
            avgRating = product.avgRating,
            reviewCount = product.reviewCount,
            options = product.options.map { ProductOptionResponse.from(product, it) },
            createdAt = product.createdAt,
            dawnDeliveryEligible = product.dawnDeliveryEligible,
        )
    }
}

/** 상품 옵션(SKU) 응답 — 최종 판매가/가용 재고 노출 */
data class ProductOptionResponse(
    val id: Long,
    val name: String,
    val sku: String,
    val price: Long, // 상품 기본가 + 옵션 추가금
    val availableStock: Int,
) {
    companion object {
        fun from(product: Product, option: ProductOption) = ProductOptionResponse(
            id = requireNotNull(option.id),
            name = option.name,
            sku = option.sku,
            price = product.basePrice + option.additionalPrice,
            availableStock = option.inventory?.available ?: 0,
        )
    }
}
