package com.example.starter.domain.gift

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 선물 미수락 만료 배치 스케줄러. `gift.scheduler.enabled=true` 일 때만 빈으로 등록된다
 * ([com.example.starter.domain.subscription.DeliverySubscriptionBillingScheduler] 와 동일 패턴 —
 * 기본 비활성, 운영에서 켜기 전까지는 관리자 수동 트리거(`/api/admin/gift-claims/expire/run`)만 사용).
 */
@Component
@ConditionalOnProperty(prefix = "gift.scheduler", name = ["enabled"], havingValue = "true")
class GiftExpiryScheduler(
    private val batchService: GiftExpiryBatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${gift.scheduler.cron:0 0 4 * * *}",
        zone = "\${gift.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        val result = batchService.expireDueClaims()
        log.info("선물 만료 배치 스케줄러 실행 완료: {}", result)
    }
}
