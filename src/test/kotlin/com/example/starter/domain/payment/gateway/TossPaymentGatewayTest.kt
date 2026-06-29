package com.example.starter.domain.payment.gateway

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * 토스 어댑터 골격 단위 테스트. 외부 통신 전 검증 분기(시크릿/ paymentKey 누락)만 확인한다.
 * 실제 PG 통신은 환경 키가 필요하므로 이 환경에서는 다루지 않는다.
 */
class TossPaymentGatewayTest {

    @Test
    fun `시크릿 키가 없으면 통신 없이 거절한다`() {
        val gateway = TossPaymentGateway(
            PaymentProperties(gateway = "toss", toss = PaymentProperties.Toss(secretKey = "")),
        )
        val result = gateway.approve(PaymentApproveCommand("ORD-20260628-AAA", 10_000, paymentKey = "pk_test"))
        assertFalse(result.success)
    }

    @Test
    fun `paymentKey 가 없으면 통신 없이 거절한다`() {
        val gateway = TossPaymentGateway(
            PaymentProperties(gateway = "toss", toss = PaymentProperties.Toss(secretKey = "test_sk_xxx")),
        )
        val result = gateway.approve(PaymentApproveCommand("ORD-20260628-BBB", 10_000, paymentKey = null))
        assertFalse(result.success)
    }
}
