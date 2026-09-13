package com.example.starter.domain.gift.entity

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
 * 선물 링크 정책. 단일 행을 유지하며 관리자가 만료기한을 런타임 변경한다
 * ([com.example.starter.domain.point.entity.PointPolicy] 와 동일 패턴).
 *
 * [expiryDays] 는 `docs/planning/gift-order.md` §9 오픈이슈 #2 — 운영 정책 확정 전까지는 7일을
 * 기본값으로 둔다(기획서 제안값).
 */
@Entity
@Table(name = "gift_policies")
class GiftPolicy(
    @Column(name = "expiry_days", nullable = false)
    var expiryDays: Int = 7,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 링크 발급 시점([from]) 기준 만료 시각. */
    fun expiresAtFrom(from: Instant): Instant = from.plus(expiryDays.toLong(), ChronoUnit.DAYS)
}
