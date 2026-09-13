package com.example.starter.domain.settlement.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 플랫폼 수수료 정책. 단일 행을 유지하며 관리자가 수수료율을 런타임 변경한다.
 * [commissionRateBp] 는 basis point(1000 = 10%).
 */
@Entity
@Table(name = "settlement_policies")
class SettlementPolicy(
    @Column(name = "commission_rate_bp", nullable = false)
    var commissionRateBp: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 판매액에 대한 수수료(원 미만 절사). */
    fun commissionFor(salesAmount: Long): Long = salesAmount * commissionRateBp / 10_000
}
