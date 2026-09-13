package com.example.starter.domain.membership

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 멤버십 정기결제 스케줄러 설정. `membership.scheduler` 로 배치 실행을 제어한다
 * ([com.example.starter.domain.settlement.SettlementProperties] 와 동일 패턴). 기본은 비활성이며,
 * 운영에서 `membership.scheduler.enabled=true` + cron 으로 켠다.
 */
@ConfigurationProperties(prefix = "membership")
data class MembershipProperties(
    val scheduler: Scheduler = Scheduler(),
) {
    data class Scheduler(
        val enabled: Boolean = false,
        /** 자동 정기결제 cron. 기본: 매일 03:00(KST) — 다음 결제일/유예 종료 도래 여부를 그때그때 판정. */
        val cron: String = "0 0 3 * * *",
        val zone: String = "Asia/Seoul",
    )
}
