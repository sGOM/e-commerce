package com.example.starter.domain.payment.gateway

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 모의 PG 어댑터. 실제 외부 통신 없이 동기로 승인을 흉내 낸다(기본 어댑터).
 *
 * MVP 규칙: 결제 금액이 0보다 크면 승인(가상 거래번호 발급), 0 이하면 거절.
 * `payment.gateway=toss` 로 전환하면 [TossPaymentGateway] 가 대신 활성화된다.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = ["gateway"], havingValue = "mock", matchIfMissing = true)
class MockPaymentGateway : PaymentGateway {

    override fun approve(command: PaymentApproveCommand): PaymentApproveResult {
        if (command.amount <= 0) {
            return PaymentApproveResult(success = false, transactionId = null, message = "유효하지 않은 결제 금액")
        }
        val transactionId = "MOCK-" + UUID.randomUUID().toString().substring(0, 12).uppercase()
        return PaymentApproveResult(success = true, transactionId = transactionId, message = "승인")
    }
}
