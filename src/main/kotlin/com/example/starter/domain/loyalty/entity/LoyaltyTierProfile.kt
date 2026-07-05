package com.example.starter.domain.loyalty.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 회원별 로열티 등급 스냅샷. 배치([com.example.starter.domain.loyalty.LoyaltyTierBatchService])가
 * 최근 12개월 순구매액을 재집계해 매 실행마다 갱신한다(강등 포함, 롤링 윈도우).
 *
 * [userId] 는 다른 애그리거트 참조를 순수 id 로 두는 이 코드베이스 관례를 따른다.
 */
@Entity
@Table(name = "loyalty_tier_profiles")
class LoyaltyTierProfile(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var tier: LoyaltyTier = LoyaltyTier.BRONZE
        protected set

    @Column(name = "net_purchase_amount", nullable = false)
    var netPurchaseAmount: Long = 0
        protected set

    @Column(name = "calculated_at", nullable = false)
    var calculatedAt: Instant = Instant.now()
        protected set

    /** 배치 재계산 결과 반영(승급/강등 모두 이 메서드 하나로 처리). */
    fun applyRecalculation(tier: LoyaltyTier, netPurchaseAmount: Long, at: Instant) {
        this.tier = tier
        this.netPurchaseAmount = netPurchaseAmount
        this.calculatedAt = at
    }
}
