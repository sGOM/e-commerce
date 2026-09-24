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
 * 결제 취소(전액·부분) 명령. [transactionId] 는 승인 때 받은 PG 거래 키(토스는 paymentKey).
 * [idempotencyKey] 는 같은 취소를 재시도해도 PG 가 한 번만 처리하도록 호출자가 결정적으로 만든다.
 */
data class PaymentCancelCommand(
    val transactionId: String?,
    val amount: Long,
    val reason: String,
    val idempotencyKey: String,
)

/** 결제 취소 결과 */
data class PaymentCancelResult(
    val success: Boolean,
    val message: String,
)

/** PG 에서 다시 조회한 결제의 현재 상태. [orderNumber] 는 승인 때 보낸 우리 주문번호(토스 `orderId`). */
data class PaymentLookupResult(
    val paymentKey: String,
    val orderNumber: String,
    val status: String,
)

/**
 * 결제 게이트웨이 추상화. MVP 는 [MockPaymentGateway] 로 동작하며, 추후 실 PG(토스 등)로
 * 구현체만 교체한다. 서비스 계층은 이 인터페이스에만 의존한다.
 */
interface PaymentGateway {
    fun approve(command: PaymentApproveCommand): PaymentApproveResult

    fun cancel(command: PaymentCancelCommand): PaymentCancelResult

    /** PG 의 결제 현재 상태 재조회(웹훅 검증용). 조회 수단이 없으면 null, 통신 오류는 예외로 전파한다. */
    fun lookup(transactionId: String): PaymentLookupResult?
}
