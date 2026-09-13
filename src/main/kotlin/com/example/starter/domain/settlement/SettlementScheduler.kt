package com.example.starter.domain.settlement

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주기 자동 정산. `settlement.scheduler.enabled=true` 일 때만 빈으로 등록된다.
 *
 * 정산 자체는 [SettlementService.generate] 가 미정산 SubOrder(`settlement_id IS NULL`)만 집계하므로
 * 멱등하다 — 같은 주문이 두 번 정산되지 않는다. 기본 단일 스케줄러 스레드가 실행을 직렬화하며,
 * 관리자 수동 트리거와도 동일 로직을 공유한다.
 */
@Component
@ConditionalOnProperty(prefix = "settlement.scheduler", name = ["enabled"], havingValue = "true")
class SettlementScheduler(
    private val settlementService: SettlementService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${settlement.scheduler.cron:0 0 4 * * MON}",
        zone = "\${settlement.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        val created = settlementService.generate()
        log.info("주기 자동 정산 완료: 정산서 {}건 생성", created.size)
    }
}
