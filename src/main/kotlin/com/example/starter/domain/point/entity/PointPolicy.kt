package com.example.starter.domain.point.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 포인트 적립 정책. 단일 행을 유지하며 관리자가 적립률을 런타임 변경한다.
 *
 * [earnRateBp] 는 basis point(100 = 1%). 결제 확정 금액에 곱해 적립액을 계산한다.
 */
@Entity
@Table(name = "point_policies")
class PointPolicy(
    @Column(name = "earn_rate_bp", nullable = false)
    var earnRateBp: Int,

    @Column(name = "expiry_days", nullable = false)
    var expiryDays: Int = 365,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 결제 금액에 대한 적립액(원 미만 절사). */
    fun calculateEarn(payableAmount: Long): Long = payableAmount * earnRateBp / 10_000

    /** 적립 시점([from]) 기준 만료 시각. */
    fun expiresAtFrom(from: Instant): Instant = from.plus(expiryDays.toLong(), ChronoUnit.DAYS)
}
