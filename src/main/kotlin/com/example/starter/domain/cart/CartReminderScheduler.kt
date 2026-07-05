package com.example.starter.domain.cart

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주기 장바구니 이탈 리마인드 스캔. `cart-reminder.scheduler.enabled=true` 일 때만 빈으로 등록된다
 * ([com.example.starter.domain.gift.GiftExpiryScheduler] 와 동일 패턴 — 기본 비활성, 운영에서 켜기
 * 전까지는 관리자 수동 트리거(`/api/admin/cart-reminders/run`)만 사용).
 */
@Component
@ConditionalOnProperty(prefix = "cart-reminder.scheduler", name = ["enabled"], havingValue = "true")
class CartReminderScheduler(
    private val cartReminderBatchService: CartReminderBatchService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${cart-reminder.scheduler.cron:0 0 * * * *}",
        zone = "\${cart-reminder.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        val result = cartReminderBatchService.sendReminders()
        log.info("장바구니 이탈 리마인드 스케줄러 실행 완료: {}", result)
    }
}
