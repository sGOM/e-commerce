package com.example.starter.domain.gift.dto

import com.example.starter.domain.gift.entity.GiftClaim
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.order.dto.ShippingAddressRequest
import com.example.starter.domain.order.entity.Order
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import java.time.Instant

/** 선물 수락(배송지 입력) 요청. 로그인 불필요 — 토큰이 유일한 인가 수단이다(AC6/AC8). */
data class GiftClaimRequest(
    @field:Valid
    @field:NotNull
    val shippingAddress: ShippingAddressRequest?,
)

/** 선물 미리보기 항목(가격은 노출하지 않는다 — 수령자에게는 구매금액을 알릴 필요가 없다). */
data class GiftPreviewItemResponse(
    val productName: String,
    val optionName: String,
    val quantity: Int,
)

/**
 * 선물 미리보기 응답(`GET /api/gift/{token}`, 비회원 접근 가능). 보낸 사람 정보는 이름만 노출하고
 * 연락처는 노출하지 않는다(§9 오픈이슈 #3과 대칭되는 반대 방향 프라이버시 보호).
 */
data class GiftPreviewResponse(
    val orderNumber: String,
    val senderName: String,
    val giftMessage: String?,
    val status: GiftClaimStatus,
    val expiresAt: Instant,
    val items: List<GiftPreviewItemResponse>,
) {
    companion object {
        fun from(claim: GiftClaim, order: Order) = GiftPreviewResponse(
            orderNumber = order.orderNumber,
            senderName = order.ordererName,
            giftMessage = order.giftMessage,
            status = claim.status,
            expiresAt = claim.expiresAt,
            items = order.subOrders.flatMap { subOrder ->
                subOrder.items.map { GiftPreviewItemResponse(it.productName, it.optionName, it.quantity) }
            },
        )
    }
}

/** 선물 링크 상태 응답 — 수령자의 수락 결과 및 구매자/관리자의 상태 조회에 공용으로 쓰인다. */
data class GiftClaimResponse(
    val orderId: Long,
    val orderNumber: String,
    val token: String,
    val status: GiftClaimStatus,
    val expiresAt: Instant,
    val claimedAt: Instant?,
) {
    companion object {
        fun from(claim: GiftClaim, orderNumber: String) = GiftClaimResponse(
            orderId = claim.orderId,
            orderNumber = orderNumber,
            token = claim.token,
            status = claim.status,
            expiresAt = claim.expiresAt,
            claimedAt = claim.claimedAt,
        )
    }
}

/** 선물 링크 정책 응답(관리자). */
data class GiftPolicyResponse(
    val expiryDays: Int,
)

/** 선물 링크 정책 변경 요청(관리자, 부분 업데이트). */
data class UpdateGiftPolicyRequest(
    val expiryDays: Int?,
)

/** 선물 미수락 만료 배치 실행 결과. */
data class GiftExpiryBatchResult(
    val expiredCount: Int,
    val erroredCount: Int,
)
