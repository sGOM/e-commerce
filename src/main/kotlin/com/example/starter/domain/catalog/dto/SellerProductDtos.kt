package com.example.starter.domain.catalog.dto

import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero

/** 상품 옵션 등록 요청 */
data class CreateOptionRequest(
    @field:NotBlank val name: String?,
    @field:NotBlank val sku: String?,
    @field:PositiveOrZero val additionalPrice: Long = 0,
    @field:PositiveOrZero val stockQuantity: Int = 0,
)

/** 상품 등록 요청(판매자) */
data class CreateProductRequest(
    @field:NotBlank val name: String?,
    @field:NotNull @field:PositiveOrZero val basePrice: Long?,
    val categoryId: Long? = null,
    val description: String? = null,
    val status: ProductStatus = ProductStatus.DRAFT,
    @field:Valid @field:NotEmpty val options: List<CreateOptionRequest> = emptyList(),
)

/** 상품 수정 요청(판매자) — 기본 정보. 옵션/재고는 별도 API. */
data class UpdateProductRequest(
    @field:NotBlank val name: String?,
    @field:NotNull @field:PositiveOrZero val basePrice: Long?,
    val categoryId: Long? = null,
    val description: String? = null,
    @field:NotNull val status: ProductStatus?,
)

/** 재고 조정 요청 — 옵션 재고 절대값 설정 */
data class AdjustStockRequest(
    @field:NotNull val optionId: Long?,
    @field:NotNull @field:PositiveOrZero val quantity: Int?,
)

/** 판매자용 옵션 응답(재고 상세 포함) */
data class SellerOptionResponse(
    val optionId: Long,
    val name: String,
    val sku: String,
    val additionalPrice: Long,
    val quantity: Int,
    val reserved: Int,
    val available: Int,
) {
    companion object {
        fun from(option: ProductOption): SellerOptionResponse {
            val inv = option.inventory
            return SellerOptionResponse(
                optionId = requireNotNull(option.id),
                name = option.name,
                sku = option.sku,
                additionalPrice = option.additionalPrice,
                quantity = inv?.quantity ?: 0,
                reserved = inv?.reserved ?: 0,
                available = inv?.available ?: 0,
            )
        }
    }
}

/** 판매자용 상품 응답 */
data class SellerProductResponse(
    val productId: Long,
    val name: String,
    val basePrice: Long,
    val status: ProductStatus,
    val categoryId: Long?,
    val options: List<SellerOptionResponse>,
) {
    companion object {
        fun from(product: Product) = SellerProductResponse(
            productId = requireNotNull(product.id),
            name = product.name,
            basePrice = product.basePrice,
            status = product.status,
            categoryId = product.category?.id,
            options = product.options.sortedBy { it.id }.map { SellerOptionResponse.from(it) },
        )
    }
}
