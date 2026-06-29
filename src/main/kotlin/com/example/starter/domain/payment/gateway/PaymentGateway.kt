package com.example.starter.domain.payment.gateway

/** 결제 승인 요청 명령. [paymentKey] 는 실 PG(예: 토스 결제위젯)에서 발급된 키로, Mock 은 무시한다. */
data class PaymentApproveCommand(
    val orderNumber: String,
    val amount: Long,
    val paymentKey: String? = null,
)

/** 결제 승인 결과 */
data class PaymentApproveResult(
    val success: Boolean,
    val transactionId: String?,
    val message: String,
)

/**
 * 결제 게이트웨이 추상화. MVP 는 [MockPaymentGateway] 로 동작하며, 추후 실 PG(토스 등)로
 * 구현체만 교체한다. 서비스 계층은 이 인터페이스에만 의존한다.
 */
interface PaymentGateway {
    fun approve(command: PaymentApproveCommand): PaymentApproveResult
}
