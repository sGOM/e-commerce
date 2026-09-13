package com.example.starter.domain.subscription.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 정기배송 정책. [com.example.starter.domain.membership.entity.MembershipPolicy] 와 동일하게 단일
 * 행을 유지하며 관리자가 런타임 변경한다(§9 오픈이슈 #3 "연속 실패 임계치"에 대한 결정).
 *
 * 멤버십은 "재시도 횟수(maxRetryCount) + 유예기간(graceDays)"의 2단계 모델이지만, 정기배송은
 * AC7/AC8 상 실패 시 유예 없이 즉시 해당 회차를 스킵하고 다음 주기로 넘어가는 것이 원칙이라(재고/카드
 * 문제로 "다음 배송"을 기약없이 미루는 것이 소비재 정기배송 UX에 맞지 않음) 별도 유예기간을 두지
 * 않는다. 대신 [maxConsecutiveFailures] 하나로 "몇 회 연속 실패 시 자동 일시정지할지"만 관리한다 —
 * 멤버십의 N(회차 내 재시도)/M(누적 임계치) 두 값을 이 도메인에서는 하나로 단순화한 것(§9 오픈이슈 #3
 * 결정, MVP 범위).
 */
@Entity
@Table(name = "delivery_subscription_policies")
class DeliverySubscriptionPolicy(
    @Column(name = "max_consecutive_failures", nullable = false)
    var maxConsecutiveFailures: Int = 3,

    // 다음 배송일 T일 전까지만 스킵 요청 허용(AC5).
    @Column(name = "skip_deadline_days", nullable = false)
    var skipDeadlineDays: Int = 1,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
