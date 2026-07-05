package com.example.starter.domain.loyalty.dto

import com.example.starter.domain.loyalty.entity.LoyaltyTier
import com.example.starter.domain.loyalty.entity.LoyaltyTierProfile
import java.time.Instant

/** 마이페이지 "내 등급" 응답. 다음 등급까지 남은 금액을 함께 노출한다. */
data class MyLoyaltyTierResponse(
    val tier: LoyaltyTier,
    val netPurchaseAmount12m: Long,
    val nextTier: LoyaltyTier?,
    val amountToNextTier: Long?,
    val calculatedAt: Instant?,
) {
    companion object {
        fun of(profile: LoyaltyTierProfile?, nextTierThreshold: (LoyaltyTier) -> Long?): MyLoyaltyTierResponse {
            val tier = profile?.tier ?: LoyaltyTier.BRONZE
            val netAmount = profile?.netPurchaseAmount ?: 0
            val nextTier = tier.next()
            val nextThreshold = nextTierThreshold(tier)
            return MyLoyaltyTierResponse(
                tier = tier,
                netPurchaseAmount12m = netAmount,
                nextTier = nextTier,
                amountToNextTier = nextThreshold?.let { (it - netAmount).coerceAtLeast(0) },
                calculatedAt = profile?.calculatedAt,
            )
        }
    }
}

/** 관리자 등급 조회 응답(개별/목록 공용). */
data class AdminLoyaltyTierResponse(
    val userId: Long,
    val tier: LoyaltyTier,
    val netPurchaseAmount12m: Long,
    val calculatedAt: Instant?,
) {
    companion object {
        fun of(userId: Long, profile: LoyaltyTierProfile?) = AdminLoyaltyTierResponse(
            userId = userId,
            tier = profile?.tier ?: LoyaltyTier.BRONZE,
            netPurchaseAmount12m = profile?.netPurchaseAmount ?: 0,
            calculatedAt = profile?.calculatedAt,
        )
    }
}

/** 등급 재계산 배치 실행 결과. */
data class LoyaltyTierBatchResult(
    val upgradedCount: Int,
    val downgradedCount: Int,
    val unchangedCount: Int,
    val erroredCount: Int,
)
