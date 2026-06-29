package com.example.starter.domain.settlement

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 셀러 정산 설정. `settlement.scheduler` 로 주기 자동 정산을 제어한다.
 * 기본은 비활성이며, 운영에서 `settlement.scheduler.enabled=true` + cron 으로 켠다.
 */
@ConfigurationProperties(prefix = "settlement")
data class SettlementProperties(
    val scheduler: Scheduler = Scheduler(),
) {
    data class Scheduler(
        /** 주기 자동 정산 활성화 여부(기본 비활성 — 관리자 수동 트리거만). */
        val enabled: Boolean = false,
        /** 자동 정산 cron. 기본: 매주 월요일 04:00(KST). */
        val cron: String = "0 0 4 * * MON",
        /** cron 해석 타임존. */
        val zone: String = "Asia/Seoul",
    )
}
