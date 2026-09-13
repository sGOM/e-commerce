package com.example.starter.domain.billing.gateway

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 정기결제(빌링) 게이트웨이 설정. `billing.gateway` 로 어댑터를 선택한다(mock — 현재 유일 구현체).
 * 실 PG(예: 토스페이먼츠 빌링) 연동 시 `toss` 등 값을 추가하고 어댑터를 분리 등록한다.
 */
@ConfigurationProperties(prefix = "billing")
data class BillingProperties(
    val gateway: String = "mock",
)
