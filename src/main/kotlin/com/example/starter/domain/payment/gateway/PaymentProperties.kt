package com.example.starter.domain.payment.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 결제 게이트웨이 설정. `payment.gateway` 로 어댑터를 선택한다(mock/toss).
 * 실 PG(toss)는 [Toss.secretKey] 등 환경값이 채워졌을 때만 실제 통신한다.
 */
@ConfigurationProperties(prefix = "payment")
data class PaymentProperties(
    /** 사용할 어댑터: mock(기본) | toss */
    val gateway: String = "mock",
    val toss: Toss = Toss(),
) {
    data class Toss(
        val baseUrl: String = "https://api.tosspayments.com",
        val secretKey: String = "",
    )
}
