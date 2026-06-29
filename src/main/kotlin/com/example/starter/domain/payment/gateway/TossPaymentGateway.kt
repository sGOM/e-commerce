package com.example.starter.domain.payment.gateway

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * 토스페이먼츠 실 PG 어댑터. `payment.gateway=toss` 일 때 활성화된다.
 *
 * 연동 흐름: 클라이언트 결제위젯이 발급한 `paymentKey` 를 서버가 받아 승인(confirm) API 로 확정한다.
 * 시크릿 키는 Basic 인증(`{secretKey}:` 의 base64)으로 전달한다. 외부 통신/키는 환경 설정에 의존하며,
 * 키가 없거나 [PaymentApproveCommand.paymentKey] 가 없으면 통신 없이 거절로 응답한다.
 *
 * 위변조 방지: 승인 응답의 `totalAmount` 가 서버가 계산한 결제 금액과 다르면 거절한다.
 */
@Component
@ConditionalOnProperty(prefix = "payment", name = ["gateway"], havingValue = "toss")
class TossPaymentGateway(
    private val properties: PaymentProperties,
    restClientBuilder: RestClient.Builder = RestClient.builder(),
) : PaymentGateway {

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.toss.baseUrl)
        .build()

    override fun approve(command: PaymentApproveCommand): PaymentApproveResult {
        val secretKey = properties.toss.secretKey
        if (secretKey.isBlank()) {
            return PaymentApproveResult(false, null, "토스 시크릿 키가 설정되지 않았습니다.")
        }
        val paymentKey = command.paymentKey
            ?: return PaymentApproveResult(false, null, "paymentKey 가 필요합니다(결제위젯에서 발급).")

        return runCatching {
            val response = restClient.post()
                .uri("/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, basicAuth(secretKey))
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    mapOf(
                        "paymentKey" to paymentKey,
                        "orderId" to command.orderNumber,
                        "amount" to command.amount,
                    ),
                )
                .retrieve()
                .body(TossConfirmResponse::class.java)

            when {
                response?.status != "DONE" ->
                    PaymentApproveResult(false, null, "승인 실패: ${response?.status ?: "응답 없음"}")
                // 승인 금액이 서버 계산 금액과 다르면 위변조로 보고 거절(결제 본체는 롤백)
                response.totalAmount != null && response.totalAmount != command.amount ->
                    PaymentApproveResult(false, null, "승인 금액 불일치: 요청 ${command.amount}, 승인 ${response.totalAmount}")
                else ->
                    PaymentApproveResult(true, response.paymentKey, "승인")
            }
        }.getOrElse { e ->
            PaymentApproveResult(false, null, "PG 통신 오류: ${e.message}")
        }
    }

    private fun basicAuth(secretKey: String): String {
        // 토스는 'secretKey:' (콜론 뒤 빈 비밀번호)를 base64 로 인코딩한 Basic 인증을 사용한다.
        val encoded = Base64.getEncoder().encodeToString("$secretKey:".toByteArray())
        return "Basic $encoded"
    }

    /** 토스 승인 응답(필요 필드만). */
    data class TossConfirmResponse(
        val paymentKey: String? = null,
        val orderId: String? = null,
        val status: String? = null,
        val totalAmount: Long? = null,
    )
}
