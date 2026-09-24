package com.example.starter.domain.order

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 미결제 주문 만료 주기 실행. `order.unpaid-expiry.scheduler.enabled=true` 일 때만 등록된다(다른 배치와 같은
 * 기본 비활성 규칙 — 그 전에는 관리자 수동 트리거 `/api/admin/orders/expire-unpaid/run`).
 */
@Component
@ConditionalOnProperty(prefix = "order.unpaid-expiry.scheduler", name = ["enabled"], havingValue = "true")
class UnpaidOrderExpiryScheduler(
    private val unpaidOrderExpiryService: UnpaidOrderExpiryService,
) {
    @Scheduled(
        cron = "\${order.unpaid-expiry.scheduler.cron:0 */10 * * * *}",
        zone = "\${order.unpaid-expiry.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        unpaidOrderExpiryService.expireUnpaid()
    }
}
