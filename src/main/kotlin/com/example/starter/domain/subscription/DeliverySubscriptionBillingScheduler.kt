package com.example.starter.domain.subscription

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 정기배송 배치 스케줄러. `delivery-subscription.scheduler.enabled=true` 일 때만 빈으로 등록된다
 * ([com.example.starter.domain.membership.MembershipBillingScheduler] 와 동일 패턴 — 기본 비활성,
 * 운영에서 켜기 전까지는 관리자 수동 트리거(`/api/admin/delivery-subscriptions/billing/run`)만 사용).
 *
 * [DeliverySubscriptionBillingService.runDueCycles] 는 매번 "다음 회차 도래 여부"로 대상을 다시 조회하므로
 * 멱등하다 — 이미 처리된 구독은 `nextOrderAt` 이 미래로 이동해 다음 실행 때 자연히 제외된다.
 */
@Component
@ConditionalOnProperty(prefix = "delivery-subscription.scheduler", name = ["enabled"], havingValue = "true")
class DeliverySubscriptionBillingScheduler(
    private val billingService: DeliverySubscriptionBillingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${delivery-subscription.scheduler.cron:0 30 3 * * *}",
        zone = "\${delivery-subscription.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        val result = billingService.runDueCycles()
        log.info("정기배송 배치 스케줄러 실행 완료: {}", result)
    }
}
