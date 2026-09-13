package com.example.starter.domain.loyalty

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주기 로열티 등급 재계산. `loyalty.scheduler.enabled=true` 일 때만 빈으로 등록된다
 * ([com.example.starter.domain.gift.GiftExpiryScheduler] 와 동일 패턴 — 기본 비활성, 운영에서 켜기
 * 전까지는 관리자 수동 트리거(`/api/admin/loyalty-tiers/recalculate/run`)만 사용).
 */
@Component
@ConditionalOnProperty(prefix = "loyalty.scheduler", name = ["enabled"], havingValue = "true")
class LoyaltyTierScheduler(
    private val loyaltyTierBatchService: LoyaltyTierBatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${loyalty.scheduler.cron:0 0 2 * * *}",
        zone = "\${loyalty.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        val result = loyaltyTierBatchService.recalculateAll()
        log.info("로열티 등급 재계산 스케줄러 실행 완료: {}", result)
    }
}
