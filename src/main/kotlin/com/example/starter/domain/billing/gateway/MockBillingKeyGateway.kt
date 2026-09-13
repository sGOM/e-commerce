package com.example.starter.domain.billing.gateway

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 모의 빌링 게이트웨이. 실제 PG 통신 없이 동기로 발급/청구를 흉내 낸다([MockPaymentGateway]와 동일한
 * 설계 원칙).
 *
 * - 빌링키 발급은 카드번호 형식만 있으면 항상 성공한다(마지막 4자리만 추출해 저장).
 * - 청구는 금액이 0보다 크면 성공한다. 단, 결제 실패/재시도 시나리오(AC5~AC7)를 결정적으로
 *   재현할 수 있도록 **테스트 전용 훅**을 둔다 — [billingKey] 가 `"FAIL-"` 로 시작하면 항상 실패를
 *   반환한다(실제 PG 샌드박스가 특정 카드번호로 실패를 재현시키는 것과 같은 관례).
 */
@Component
@ConditionalOnProperty(prefix = "billing", name = ["gateway"], havingValue = "mock", matchIfMissing = true)
class MockBillingKeyGateway : BillingKeyGateway {

    override fun issueBillingKey(command: IssueBillingKeyCommand): IssueBillingKeyResult {
        if (command.cardNumber.length < 12) {
            return IssueBillingKeyResult(success = false, billingKey = null, cardLast4 = null, message = "유효하지 않은 카드번호")
        }
        val billingKey = "MOCK-BILLING-" + UUID.randomUUID().toString().substring(0, 12).uppercase()
        return IssueBillingKeyResult(
            success = true,
            billingKey = billingKey,
            cardLast4 = command.cardNumber.takeLast(4),
            message = "카드가 등록되었습니다.",
        )
    }

    override fun chargeBillingKey(command: ChargeBillingKeyCommand): ChargeBillingKeyResult {
        if (command.amount <= 0 || command.billingKey.startsWith("FAIL-")) {
            return ChargeBillingKeyResult(success = false, transactionId = null, message = "정기결제가 거절되었습니다.")
        }
        val transactionId = "MOCK-CHG-" + UUID.randomUUID().toString().substring(0, 12).uppercase()
        return ChargeBillingKeyResult(success = true, transactionId = transactionId, message = "승인")
    }
}
