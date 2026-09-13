package com.example.starter.domain.membership

import com.example.starter.domain.membership.dto.MembershipBenefitSummary
import com.example.starter.domain.membership.repository.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 멤버십 혜택 조회 전용 서비스. `order`/`point` 도메인은 이 서비스만 알면 되고 멤버십 내부 구현
 * (엔티티/상태 전이)에는 의존하지 않는다(기획서 §4 "혜택 판정 기준" — 조회 시점의 `ACTIVE` 여부).
 *
 * MVP 혜택 범위는 무료배송(AC8)·포인트 우대 배율(AC9) 2가지만 다룬다. 멤버십 전용 쿠폰(AC10)은
 * 범위가 커 후속 과제로 분리한다(README/기획서 참고).
 */
@Service
@Transactional(readOnly = true)
class MembershipBenefitService(
    private val membershipRepository: MembershipRepository,
    private val membershipPolicyService: MembershipPolicyService,
) {

    fun isBenefitActive(userId: Long, now: Instant = Instant.now()): Boolean =
        membershipRepository.findByUserId(userId)?.isBenefitActive(now) ?: false

    /** 무료배송 적용 여부(AC8) — 정책 스위치 + 지금 이 순간 혜택 활성 여부. */
    fun isFreeShippingActive(userId: Long, now: Instant = Instant.now()): Boolean =
        membershipPolicyService.currentPolicy().freeShippingEnabled && isBenefitActive(userId, now)

    /** 포인트 적립 배율(bp, AC9) — 혜택 비활성이면 1.0배(10000)를 반환해 정상 적립을 그대로 유지한다. */
    fun pointEarnMultiplierBp(userId: Long, now: Instant = Instant.now()): Int =
        if (isBenefitActive(userId, now)) membershipPolicyService.currentPolicy().pointEarnMultiplierBp else 10_000

    fun benefitSummary(userId: Long, now: Instant = Instant.now()) = MembershipBenefitSummary(
        freeShipping = isFreeShippingActive(userId, now),
        pointEarnMultiplierBp = pointEarnMultiplierBp(userId, now),
    )
}
