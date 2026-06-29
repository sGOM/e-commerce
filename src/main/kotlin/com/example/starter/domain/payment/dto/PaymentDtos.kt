package com.example.starter.domain.payment.dto

import com.example.starter.domain.payment.entity.Payment
import com.example.starter.domain.payment.entity.PaymentStatus
import java.time.Instant

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
