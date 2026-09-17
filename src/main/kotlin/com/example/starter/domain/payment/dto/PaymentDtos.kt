package com.example.starter.domain.payment.dto

import com.example.starter.domain.payment.entity.Payment
import com.example.starter.domain.payment.entity.PaymentStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/**
 * 결제 요청. 실 PG(토스 등)는 클라이언트 결제위젯이 발급한 [paymentKey] 를 함께 보낸다.
 * Mock PG 는 이 값을 무시한다. 본문 없이 호출하면 paymentKey = null.
 */
data class PayRequest(
    val paymentKey: String? = null,
)

/** 비회원 결제 요청 — 주문번호 + 주문 시 연락처로 본인 확인 */
data class GuestPayRequest(
    @field:NotBlank val orderNumber: String?,
    @field:NotBlank val ordererPhone: String?,
    val paymentKey: String? = null,
)

/** 결제 결과 응답 */
data class PaymentResponse(
    val paymentId: Long,
    val orderId: Long,
    val status: PaymentStatus,
    val amount: Long,
    val method: String,
    val transactionId: String?,
    val paidAt: Instant?,
) {
    companion object {
        fun from(payment: Payment) = PaymentResponse(
            paymentId = requireNotNull(payment.id),
            orderId = payment.orderId,
            status = payment.status,
            amount = payment.amount,
            method = payment.method,
            transactionId = payment.pgTransactionId,
            paidAt = if (payment.status == PaymentStatus.PAID) payment.updatedAt else null,
        )
    }
}
