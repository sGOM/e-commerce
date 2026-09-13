package com.example.starter.domain.seller.dto

import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import jakarta.validation.constraints.NotBlank

/** 입점 신청 요청 */
data class SellerApplyRequest(
    @field:NotBlank val storeName: String?,
    val description: String? = null,
)

/** 입점 심사(승인/거절) 요청 */
data class SellerApproveRequest(
    val approved: Boolean,
)

/** 판매자(상점) 응답 */
data class SellerResponse(
    val sellerId: Long,
    val userId: Long,
    val storeName: String,
    val description: String?,
    val status: SellerStatus,
) {
    companion object {
        fun from(seller: Seller) = SellerResponse(
            sellerId = requireNotNull(seller.id),
            userId = seller.userId,
            storeName = seller.storeName,
            description = seller.description,
            status = seller.status,
        )
    }
}
