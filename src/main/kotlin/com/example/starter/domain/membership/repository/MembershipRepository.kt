package com.example.starter.domain.membership.repository

import com.example.starter.domain.membership.entity.Membership
import com.example.starter.domain.membership.entity.MembershipStatus
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface MembershipRepository : JpaRepository<Membership, Long>, KotlinJdslJpqlExecutor {

    fun findByUserId(userId: Long): Membership?

    /**
     * 상태 + 기준 시각 도래 조회. 두 용도로 재사용한다:
     * - `(ACTIVE, now)`: 정상 자동갱신 대상(다음 결제일 도래) — 스케줄러 청구 대상.
     * - `(CANCELED, now)`: 해지 예약 유예 종료 대상(AC3) — 청구 없이 EXPIRED 로 전이.
     */
    fun findByStatusAndNextBillingAtLessThanEqual(status: MembershipStatus, nextBillingAt: Instant): List<Membership>

    /** PAST_DUE 재시도/최종 만료 판정 대상(유예기간 종료 도래, AC6). */
    fun findByStatusAndGracePeriodEndsAtLessThanEqual(status: MembershipStatus, gracePeriodEndsAt: Instant): List<Membership>
}
