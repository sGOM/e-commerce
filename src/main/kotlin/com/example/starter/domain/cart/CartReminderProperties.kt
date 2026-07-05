package com.example.starter.domain.cart

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 장바구니 이탈 리마인드 정책. MVP는 리마인드 알림만 발송하고 자동 쿠폰은 발급하지 않는다
 * (어뷰징 회피 — "의도적 방치 후 쿠폰 수령" 리스크 통제, product-planner 결정).
 */
@ConfigurationProperties(prefix = "cart-reminder")
data class CartReminderProperties(
    /** 마지막 활동 후 이 시간(시간 단위)이 지나도록 미결제면 이탈로 간주한다. */
    val inactivityHours: Long = 24,
    val scheduler: Scheduler = Scheduler(),
) {
    data class Scheduler(
        /** 주기 자동 스캔 활성화 여부(기본 비활성 — 관리자 수동 트리거만). */
        val enabled: Boolean = false,
        /** 자동 스캔 cron. 기본: 매시 정각(짧은 리마인드 윈도우 특성상 다른 배치보다 자주 스캔). */
        val cron: String = "0 0 * * * *",
        val zone: String = "Asia/Seoul",
    )
}
