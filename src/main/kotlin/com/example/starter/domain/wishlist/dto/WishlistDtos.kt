package com.example.starter.domain.wishlist.dto

import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.wishlist.entity.Wishlist
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class AddWishlistRequest(
    @field:NotNull
    val productId: Long?,
)

/**
 * 위시리스트 항목 응답. [baselinePrice] 대비 [currentPrice] 하락분을 매 조회 시 계산해 배지로 노출한다
 * (AC5). 상품이 삭제됐으면(§4 자동 정리 대상) [product] 가 null 로 전달된다 — 응답에서는 조용히
 * 안내 문구로 대체한다(호출측이 목록에서 걸러낼 수도 있음).
 */
data class WishlistResponse(
    val id: Long,
    val productId: Long,
    val productName: String,
    val productStatus: ProductStatus,
    val baselinePrice: Long,
    val currentPrice: Long,
    val priceDropAmount: Long,
    val priceDropRate: Int, // 정수 % (내림)
    val isPriceDropped: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(wishlist: Wishlist, product: Product?): WishlistResponse {
            val currentPrice = product?.basePrice ?: wishlist.baselinePrice
            val dropAmount = (wishlist.baselinePrice - currentPrice).coerceAtLeast(0)
            val dropRate = if (wishlist.baselinePrice > 0) (dropAmount * 100 / wishlist.baselinePrice).toInt() else 0
            return WishlistResponse(
                id = requireNotNull(wishlist.id),
                productId = wishlist.productId,
                productName = product?.name ?: "(삭제된 상품)",
                productStatus = product?.status ?: ProductStatus.HIDDEN,
                baselinePrice = wishlist.baselinePrice,
                currentPrice = currentPrice,
                priceDropAmount = dropAmount,
                priceDropRate = dropRate,
                isPriceDropped = dropAmount > 0,
                createdAt = wishlist.createdAt,
            )
        }
    }
}
