package com.example.starter.domain.loyalty

import com.example.starter.domain.loyalty.entity.LoyaltyTier
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 로열티 등급 산정 정책. 임계값은 추후 조정 가능하도록 상수 대신 설정값으로 분리했다
 * (`docs/planning` 요청사항 — "임계값은 추후 조정 가능하도록 상수/설정으로 분리").
 *
 * 기본 임계값 근거: 국내 커머스 업계에서 흔히 쓰이는 "연간 상위 20%/5%/1%" 식 티어링 관행을
 * 참고해, 12개월 순구매액 30만원(가벼운 반복구매)/100만원(단골)/300만원(핵심 고객) 을 3개 경계로
 * 잡았다. 오너 확정값(2026-07-05). 이후 실 구매 데이터 축적 시 설정값으로 재조정 가능.
 */
@ConfigurationProperties(prefix = "loyalty")
data class LoyaltyTierProperties(
    /** 순구매액 집계 롤링 윈도우(개월). */
    val lookbackMonths: Long = 12,
    /** SILVER 승급 임계값(원, 최근 [lookbackMonths]개월 순구매액 기준). */
    val silverThreshold: Long = 300_000,
    /** GOLD 승급 임계값(원). */
    val goldThreshold: Long = 1_000_000,
    /** VIP 승급 임계값(원). */
    val vipThreshold: Long = 3_000_000,
    val scheduler: Scheduler = Scheduler(),
) {
    data class Scheduler(
        /** 주기 자동 재계산 활성화 여부(기본 비활성 — 관리자 수동 트리거만). */
        val enabled: Boolean = false,
        /** 자동 재계산 cron. 기본: 매일 02:00(KST). */
        val cron: String = "0 0 2 * * *",
        val zone: String = "Asia/Seoul",
    )

    /** 순구매액으로 등급을 판정한다. */
    fun resolveTier(netPurchaseAmount: Long): LoyaltyTier = when {
        netPurchaseAmount >= vipThreshold -> LoyaltyTier.VIP
        netPurchaseAmount >= goldThreshold -> LoyaltyTier.GOLD
        netPurchaseAmount >= silverThreshold -> LoyaltyTier.SILVER
        else -> LoyaltyTier.BRONZE
    }

    /** 등급별 다음 단계 진입 임계값(원). 최상위(VIP)는 null. */
    fun nextTierThreshold(tier: LoyaltyTier): Long? = when (tier) {
        LoyaltyTier.BRONZE -> silverThreshold
        LoyaltyTier.SILVER -> goldThreshold
        LoyaltyTier.GOLD -> vipThreshold
        LoyaltyTier.VIP -> null
    }
}
