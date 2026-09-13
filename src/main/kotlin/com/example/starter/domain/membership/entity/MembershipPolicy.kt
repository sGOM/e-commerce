package com.example.starter.domain.membership.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 멤버십 정책. [PointPolicy]/[SettlementPolicy] 와 동일하게 단일 행을 유지하며 관리자가 런타임 변경한다.
 *
 * [pointEarnMultiplierBp] 는 basis point(10000 = 1.0배, 15000 = 1.5배)로, 기존
 * `PointPolicy.earnRateBp` 계산 결과에 곱해 우대 적립을 적용한다(AC9).
 * MVP 는 단일 플랜(BASIC) 가격만 관리하며, [com.example.starter.domain.membership.entity.MembershipPlan.PREMIUM]
 * 등 플랜별 차등 정책은 범위 밖이다(기획서 §8).
 */
@Entity
@Table(name = "membership_policies")
class MembershipPolicy(
    @Column(name = "monthly_price", nullable = false)
    var monthlyPrice: Long,

    @Column(name = "point_earn_multiplier_bp", nullable = false)
    var pointEarnMultiplierBp: Int = 10_000,

    @Column(name = "free_shipping_enabled", nullable = false)
    var freeShippingEnabled: Boolean = true,

    @Column(name = "max_retry_count", nullable = false)
    var maxRetryCount: Int = 3,

    @Column(name = "grace_days", nullable = false)
    var graceDays: Int = 3,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 기본 적립액에 멤버십 배율을 적용한다(원 미만 절사). */
    fun applyEarnMultiplier(baseEarn: Long): Long = baseEarn * pointEarnMultiplierBp / 10_000
}
