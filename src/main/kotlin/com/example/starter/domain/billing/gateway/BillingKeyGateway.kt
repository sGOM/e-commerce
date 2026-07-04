package com.example.starter.domain.billing.gateway

/**
 * 빌링키 발급 요청 명령. [cardNumber] 는 실 PG 연동 시 카드 위젯에서 받은 원본을 그대로 넘기되,
 * Mock 은 마지막 4자리([BillingKeyGateway.issueBillingKey] 결과의 [IssueBillingKeyResult.cardLast4])만
 * 추출해 저장한다(PCI-DSS 상 원본 카드번호는 우리 서버/DB에 영속화하지 않는다).
 */
data class IssueBillingKeyCommand(
    val userId: Long,
    val cardNumber: String,
)

data class IssueBillingKeyResult(
    val success: Boolean,
    val billingKey: String?,
    val cardLast4: String?,
    val message: String,
)

/** 등록된 빌링키로 정기결제(자동승인) 1건을 실행하는 명령. [orderRef] 는 로그/추적용 참조 문자열이다. */
data class ChargeBillingKeyCommand(
    val billingKey: String,
    val amount: Long,
    val orderRef: String,
)

data class ChargeBillingKeyResult(
    val success: Boolean,
    val transactionId: String?,
    val message: String,
)

/**
 * 정기결제(빌링) 게이트웨이 추상화 — 1회성 결제 전용인
 * [com.example.starter.domain.payment.gateway.PaymentGateway] 와는 별도 계약이다(토스페이먼츠도
 * 빌링 전용 API가 분리되어 있어 실 연동 시 어댑터를 나눈다, 기획서 §5).
 *
 * `membership`(구독) 도메인이 최초 사용처이며, 후속 기능인 정기배송(subscription-delivery)도 이
 * 포트를 그대로 재사용하도록 공통 패키지(`domain.billing`)에 둔다. 서비스 계층은 이 인터페이스에만
 * 의존하고, 실 PG 연동은 구현체 교체만으로 대응한다(MVP는 [MockBillingKeyGateway]).
 */
interface BillingKeyGateway {
    fun issueBillingKey(command: IssueBillingKeyCommand): IssueBillingKeyResult
    fun chargeBillingKey(command: ChargeBillingKeyCommand): ChargeBillingKeyResult
}
