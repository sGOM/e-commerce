package com.example.starter.domain.subscription.dto

import com.example.starter.domain.order.dto.ShippingAddressRequest
import com.example.starter.domain.subscription.entity.DeliverySubscription
import com.example.starter.domain.subscription.entity.DeliverySubscriptionBillingKey
import com.example.starter.domain.subscription.entity.DeliverySubscriptionHistory
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import java.time.Instant

/** 빌링키(카드) 등록/재등록 요청. Mock 게이트웨이는 형식만 검증하고 실 카드 통신은 하지 않는다. */
data class RegisterDeliverySubscriptionBillingKeyRequest(
    @field:NotBlank
    @field:Pattern(regexp = "\\d{12,16}", message = "카드번호는 숫자 12~16자리여야 합니다.")
    val cardNumber: String?,
)

data class DeliverySubscriptionBillingKeyResponse(
    val cardLast4: String,
    val registeredAt: Instant,
) {
    companion object {
        fun from(key: DeliverySubscriptionBillingKey) = DeliverySubscriptionBillingKeyResponse(
            cardLast4 = key.cardLast4,
            registeredAt = key.createdAt,
        )
    }
}

/**
 * 정기배송 등록 요청(AC1). [startImmediately] 는 등록 즉시 첫 회차를 바로 주문·결제할지, 다음 주기부터
 * 시작할지를 고른다(AC2, 기본값은 즉시 시작).
 */
data class CreateDeliverySubscriptionRequest(
    @field:NotNull val optionId: Long?,
    @field:NotNull @field:Min(1) val quantity: Int?,
    @field:NotNull @field:Min(1) val cycleDays: Int?,
    @field:NotBlank val ordererName: String?,
    @field:NotBlank val ordererPhone: String?,
    @field:NotBlank val ordererEmail: String?,
    @field:Valid @field:NotNull val shippingAddress: ShippingAddressRequest?,
    val startImmediately: Boolean = true,
)

data class DeliverySubscriptionResponse(
    val id: Long,
    val optionId: Long,
    val productName: String,
    val optionName: String,
    val quantity: Int,
    val cycleDays: Int,
    val status: DeliverySubscriptionStatus,
    val nextOrderAt: Instant,
    val skipRequested: Boolean,
    val consecutiveFailureCount: Int,
    val canceledAt: Instant?,
) {
    companion object {
        fun from(subscription: DeliverySubscription, productName: String, optionName: String) = DeliverySubscriptionResponse(
            id = requireNotNull(subscription.id),
            optionId = subscription.optionId,
            productName = productName,
            optionName = optionName,
            quantity = subscription.quantity,
            cycleDays = subscription.cycleDays,
            status = subscription.status,
            nextOrderAt = subscription.nextOrderAt,
            skipRequested = subscription.skipRequested,
            consecutiveFailureCount = subscription.consecutiveFailureCount,
            canceledAt = subscription.canceledAt,
        )
    }
}

/** 회차별 처리 이력(AC10). */
data class DeliverySubscriptionHistoryResponse(
    val id: Long,
    val attemptedAt: Instant,
    val result: String,
    val orderId: Long?,
    val detail: String?,
) {
    companion object {
        fun from(history: DeliverySubscriptionHistory) = DeliverySubscriptionHistoryResponse(
            id = requireNotNull(history.id),
            attemptedAt = history.attemptedAt,
            result = history.result.name,
            orderId = history.orderId,
            detail = history.detail,
        )
    }
}

/** 관리자 현황 검색 조건. */
data class AdminDeliverySubscriptionSearchCondition(
    val status: DeliverySubscriptionStatus? = null,
)

/** 관리자 현황 응답 — 회원 식별자를 포함해 운영에 필요한 정보를 노출한다. */
data class AdminDeliverySubscriptionResponse(
    val id: Long,
    val userId: Long,
    val optionId: Long,
    val quantity: Int,
    val cycleDays: Int,
    val status: DeliverySubscriptionStatus,
    val nextOrderAt: Instant,
    val skipRequested: Boolean,
    val consecutiveFailureCount: Int,
    val canceledAt: Instant?,
) {
    companion object {
        fun from(subscription: DeliverySubscription) = AdminDeliverySubscriptionResponse(
            id = requireNotNull(subscription.id),
            userId = subscription.userId,
            optionId = subscription.optionId,
            quantity = subscription.quantity,
            cycleDays = subscription.cycleDays,
            status = subscription.status,
            nextOrderAt = subscription.nextOrderAt,
            skipRequested = subscription.skipRequested,
            consecutiveFailureCount = subscription.consecutiveFailureCount,
            canceledAt = subscription.canceledAt,
        )
    }
}

data class DeliverySubscriptionPolicyResponse(
    val maxConsecutiveFailures: Int,
    val skipDeadlineDays: Int,
)

/** 정책 부분 업데이트 요청(전달한 항목만 변경). */
data class UpdateDeliverySubscriptionPolicyRequest(
    val maxConsecutiveFailures: Int? = null,
    val skipDeadlineDays: Int? = null,
)
