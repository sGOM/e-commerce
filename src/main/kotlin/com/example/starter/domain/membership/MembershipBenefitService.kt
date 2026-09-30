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
 * 혜택은 포인트 우대 배율(AC9)이다. 무료배송(AC8)은 배송비가 판매자 몫이 되면서(ROADMAP 7.3, 2026-10-01) 없앴다 —
 * 판매자가 받는 택배비를 플랫폼 혜택으로 깎을 수 없기 때문이다.
 */
@Service
@Transactional(readOnly = true)
class MembershipBenefitService(
    private val membershipRepository: MembershipRepository,
    private val membershipPolicyService: MembershipPolicyService,
) {

    fun isBenefitActive(userId: Long, now: Instant = Instant.now()): Boolean =
        membershipRepository.findByUserId(userId)?.isBenefitActive(now) ?: false

    /** 포인트 적립 배율(bp, AC9) — 혜택 비활성이면 1.0배(10000)를 반환해 정상 적립을 그대로 유지한다. */
    fun pointEarnMultiplierBp(userId: Long, now: Instant = Instant.now()): Int =
        if (isBenefitActive(userId, now)) membershipPolicyService.currentPolicy().pointEarnMultiplierBp else 10_000

    fun benefitSummary(userId: Long, now: Instant = Instant.now()) = MembershipBenefitSummary(
        pointEarnMultiplierBp = pointEarnMultiplierBp(userId, now),
    )
}
