package com.example.starter.domain.membership.entity

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
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 유료 멤버십 구독. 회원당 1행을 재사용한다(해지/만료 후 재가입해도 새 행을 만들지 않고 상태만
 * 초기화 — `uk_memberships_user` unique 제약, 기획서 §5 "활성 구독은 1개").
 *
 * 상태 전이/혜택 판정은 "조회 시점 판정" 원칙을 따른다: 스케줄러([com.example.starter.domain.membership.MembershipBillingScheduler])가
 * 주기적으로 상태를 정산하지만, 그 사이(배치 미실행 구간)에도 [isBenefitActive] 가 `canceledAt`/
 * `nextBillingAt` 을 함께 봐서 스스로 만료 여부를 계산한다 — 배치 지연이 혜택 오적용으로 이어지지
 * 않게 하는 자기 보정(self-healing) 설계다.
 */
@Entity
@Table(name = "memberships")
class Membership(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var plan: MembershipPlan = MembershipPlan.BASIC

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MembershipStatus = MembershipStatus.ACTIVE

    @Column(nullable = false)
    var price: Long = 0

    @Column(name = "start_at", nullable = false)
    var startAt: Instant = Instant.now()

    @Column(name = "next_billing_at", nullable = false)
    var nextBillingAt: Instant = Instant.now()

    @Column(name = "canceled_at")
    var canceledAt: Instant? = null

    @Column(name = "billing_failure_count", nullable = false)
    var billingFailureCount: Int = 0

    @Column(name = "grace_period_ends_at")
    var gracePeriodEndsAt: Instant? = null

    /** 최초 가입/재가입 시 활성화(AC1). 결제(첫 청구)가 성공한 뒤 호출한다. */
    fun activate(plan: MembershipPlan, price: Long, now: Instant, cycleMonths: Long) {
        this.plan = plan
        this.status = MembershipStatus.ACTIVE
        this.price = price
        this.startAt = now
        this.nextBillingAt = plusMonths(now, cycleMonths)
        this.canceledAt = null
        this.billingFailureCount = 0
        this.gracePeriodEndsAt = null
    }

    /** 해지 예약(AC3). 즉시 상태를 CANCELED 로 표시하지만 혜택은 [nextBillingAt] 까지 유지된다. */
    fun scheduleCancel(now: Instant) {
        status = MembershipStatus.CANCELED
        canceledAt = now
    }

    /** 정기결제 성공 — 다음 주기로 갱신(AC7). */
    fun renew(now: Instant, cycleMonths: Long) {
        status = MembershipStatus.ACTIVE
        nextBillingAt = plusMonths(nextBillingAt, cycleMonths)
        billingFailureCount = 0
        gracePeriodEndsAt = null
    }

    /** 정기결제 실패 — 유예기간을 부여하고 PAST_DUE 로 전이(AC6). */
    fun markPastDue(now: Instant, graceDays: Int) {
        status = MembershipStatus.PAST_DUE
        billingFailureCount += 1
        gracePeriodEndsAt = now.plusSeconds(graceDays * SECONDS_PER_DAY)
    }

    /** 재시도/유예 모두 소진되었거나 해지 유예 기간이 끝나 혜택을 종료(AC3/AC6). */
    fun expire() {
        status = MembershipStatus.EXPIRED
        gracePeriodEndsAt = null
    }

    /**
     * 지금([now]) 이 순간 혜택이 적용돼야 하는지. 결제/주문 시점 판정 기준이다(기획서 §4).
     * CANCELED 는 해지 예약 상태이므로 이미 결제한 기간([nextBillingAt] 이전)까지만 유효 —
     * 스케줄러가 아직 EXPIRED 로 못 바꿨어도(배치 지연) 여기서 자체적으로 걸러진다.
     */
    fun isBenefitActive(now: Instant): Boolean = when (status) {
        MembershipStatus.ACTIVE, MembershipStatus.PAST_DUE -> true
        MembershipStatus.CANCELED -> now.isBefore(nextBillingAt)
        MembershipStatus.EXPIRED -> false
    }

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
        private val KST = ZoneId.of("Asia/Seoul")

        /** Instant 는 달력 단위(MONTHS) 가산을 지원하지 않아 KST 기준 ZonedDateTime 을 경유한다. */
        fun plusMonths(instant: Instant, months: Long): Instant =
            ZonedDateTime.ofInstant(instant, KST).plusMonths(months).toInstant()
    }
}
