package com.example.starter.domain.payment.gateway

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * 토스 어댑터 단위 테스트. 검증 분기(시크릿/paymentKey 누락)와 실제 confirm 호출(성공/실패/금액 불일치)을
 * [MockRestServiceServer] 로 외부 통신을 흉내 내어 검증한다.
 */
class TossPaymentGatewayTest {

    private val baseUrl = "https://api.tosspayments.test"
    private val secretKey = "test_sk_xxx"

    private fun props() = PaymentProperties(
        gateway = "toss",
        toss = PaymentProperties.Toss(baseUrl = baseUrl, secretKey = secretKey),
    )

    /** MockRestServiceServer 가 바인딩된 builder 로 게이트웨이를 만들고 (게이트웨이, 서버)를 반환. */
    private fun gatewayWithMockServer(properties: PaymentProperties): Pair<TossPaymentGateway, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return TossPaymentGateway(properties, builder) to server
    }

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
            PaymentProperties(gateway = "toss", toss = PaymentProperties.Toss(secretKey = secretKey)),
        )
        val result = gateway.approve(PaymentApproveCommand("ORD-20260628-BBB", 10_000, paymentKey = null))
        assertFalse(result.success)
    }

    @Test
    fun `confirm 이 DONE 이고 금액이 일치하면 승인한다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        val expectedAuth = "Basic " + Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
        server.expect(requestTo("$baseUrl/v1/payments/confirm"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
            .andExpect(jsonPath("$.paymentKey").value("pk_live_1"))
            .andExpect(jsonPath("$.amount").value(10_000))
            .andRespond(
                withSuccess(
                    """{"paymentKey":"pk_live_1","orderId":"ORD-1","status":"DONE","totalAmount":10000}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.approve(PaymentApproveCommand("ORD-1", 10_000, paymentKey = "pk_live_1"))

        assertTrue(result.success)
        assertEquals("pk_live_1", result.transactionId)
        server.verify()
    }

    @Test
    fun `confirm 상태가 DONE 이 아니면 거절한다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        server.expect(requestTo("$baseUrl/v1/payments/confirm"))
            .andRespond(
                withSuccess("""{"status":"ABORTED"}""", MediaType.APPLICATION_JSON),
            )

        val result = gateway.approve(PaymentApproveCommand("ORD-2", 10_000, paymentKey = "pk_x"))

        assertFalse(result.success)
        server.verify()
    }

    @Test
    fun `승인 금액이 요청 금액과 다르면 위변조로 거절한다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        server.expect(requestTo("$baseUrl/v1/payments/confirm"))
            .andRespond(
                withSuccess(
                    """{"paymentKey":"pk_y","status":"DONE","totalAmount":9999}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.approve(PaymentApproveCommand("ORD-3", 10_000, paymentKey = "pk_y"))

        assertFalse(result.success)
        server.verify()
    }

    @Test
    fun `PG 통신 오류는 거절로 처리한다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        server.expect(requestTo("$baseUrl/v1/payments/confirm"))
            .andRespond(withServerError())

        val result = gateway.approve(PaymentApproveCommand("ORD-4", 10_000, paymentKey = "pk_z"))

        assertFalse(result.success)
        server.verify()
    }
    @Test
    fun `cancel 은 paymentKey 취소 API 를 금액·사유·멱등키와 함께 호출한다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        server.expect(requestTo("$baseUrl/v1/payments/pk_c1/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Idempotency-Key", "cancel-sub-7"))
            .andExpect(jsonPath("$.cancelAmount").value(3_000))
            .andExpect(jsonPath("$.cancelReason").value("부분 취소"))
            .andRespond(withSuccess("""{"paymentKey":"pk_c1","status":"PARTIAL_CANCELED"}""", MediaType.APPLICATION_JSON))

        val result = gateway.cancel(PaymentCancelCommand("pk_c1", 3_000, "부분 취소", "cancel-sub-7"))

        assertTrue(result.success)
        server.verify()
    }

    @Test
    fun `cancel 응답이 취소 상태가 아니거나 통신 오류면 실패다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        server.expect(requestTo("$baseUrl/v1/payments/pk_c2/cancel"))
            .andRespond(withSuccess("""{"status":"DONE"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("$baseUrl/v1/payments/pk_c3/cancel"))
            .andRespond(withServerError())

        assertFalse(gateway.cancel(PaymentCancelCommand("pk_c2", 1_000, "취소", "k2")).success)
        assertFalse(gateway.cancel(PaymentCancelCommand("pk_c3", 1_000, "취소", "k3")).success)
        server.verify()
    }

    @Test
    fun `lookup 은 결제 단건 조회로 PG 의 현재 상태와 주문번호를 돌려준다`() {
        val (gateway, server) = gatewayWithMockServer(props())
        server.expect(requestTo("$baseUrl/v1/payments/pk_l1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess("""{"paymentKey":"pk_l1","orderId":"ORD-9","status":"CANCELED"}""", MediaType.APPLICATION_JSON),
            )

        assertEquals(PaymentLookupResult("pk_l1", "ORD-9", "CANCELED"), gateway.lookup("pk_l1"))
        server.verify()
    }

    @Test
    fun `거래 키가 없으면 통신 없이 취소 실패다`() {
        val gateway = TossPaymentGateway(props())
        assertFalse(gateway.cancel(PaymentCancelCommand(null, 1_000, "취소", "k")).success)
    }
}
