package com.example.starter.domain.membership

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.billing.gateway.BillingKeyGateway
import com.example.starter.domain.billing.gateway.ChargeBillingKeyCommand
import com.example.starter.domain.membership.dto.AdminMembershipResponse
import com.example.starter.domain.membership.dto.AdminMembershipSearchCondition
import com.example.starter.domain.membership.dto.MembershipBillingHistoryResponse
import com.example.starter.domain.membership.entity.BillingHistoryStatus
import com.example.starter.domain.membership.entity.Membership
import com.example.starter.domain.membership.entity.MembershipBillingHistory
import com.example.starter.domain.membership.entity.MembershipStatus
import com.example.starter.domain.membership.repository.MembershipBillingHistoryRepository
import com.example.starter.domain.membership.repository.MembershipBillingKeyRepository
import com.example.starter.domain.membership.repository.MembershipRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 정기결제 실행/재시도/유예·만료 판정([MembershipBillingScheduler]가 주기 호출) + 관리자 조회.
 *
 * 결제 게이트웨이는 실패를 예외가 아니라 `success=false` 로 반환하므로([BillingKeyGateway] —
 * [com.example.starter.domain.payment.gateway.PaymentGateway] 와 동일 관례), [runDueBilling] 은
 * [com.example.starter.domain.settlement.SettlementService.generate] 처럼 대상 전체를 한 트랜잭션에서
 * 처리한다(개별 실패가 예외로 전체를 롤백시키지 않는다). 다만 여러 스케줄러 인스턴스가 동시에
 * 같은 멤버십을 처리하는 경쟁 상황까지는 막지 않는다 — 단일 인스턴스 전제([SettlementScheduler] 와
 * 동일한 위험 수용 범위이며, 실제 이중 청구는 DB 부분 유니크 인덱스로 막는다).
 */
@Service
@Transactional(readOnly = true)
class MembershipBillingService(
    private val membershipRepository: MembershipRepository,
    private val billingKeyRepository: MembershipBillingKeyRepository,
    private val billingHistoryRepository: MembershipBillingHistoryRepository,
    private val billingKeyGateway: BillingKeyGateway,
    private val membershipPolicyService: MembershipPolicyService,
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 다음 결제일 도래(자동갱신/해지유예종료) + PAST_DUE 재시도 대상을 한 번에 처리한다. */
    @Transactional
    fun runDueBilling(now: Instant = Instant.now()): MembershipBillingRunResult {
        val policy = membershipPolicyService.currentPolicy()
        var renewed = 0
        var failed = 0
        var expired = 0

        // 1) 정상 자동갱신 대상(ACTIVE, 다음 결제일 도래) — 청구 시도
        membershipRepository.findByStatusAndNextBillingAtLessThanEqual(MembershipStatus.ACTIVE, now).forEach { m ->
            if (charge(m, now, policy.monthlyPrice, policy.graceDays, CYCLE_MONTHS)) renewed++ else failed++
        }

        // 2) 해지 예약 유예 종료(CANCELED, 결제한 기간 만료) — 청구 없이 EXPIRED 로 전이(AC3)
        membershipRepository.findByStatusAndNextBillingAtLessThanEqual(MembershipStatus.CANCELED, now).forEach { m ->
            m.expire()
            notify(m, "멤버십이 종료되었습니다.", "해지 예약하신 멤버십의 이용 기간이 만료되어 혜택이 종료되었습니다.")
            expired++
        }

        // 3) PAST_DUE 재시도/최종 만료 판정(유예기간 종료 도래)
        membershipRepository.findByStatusAndGracePeriodEndsAtLessThanEqual(MembershipStatus.PAST_DUE, now).forEach { m ->
            if (m.billingFailureCount >= policy.maxRetryCount) {
                m.expire()
                notify(m, "멤버십이 종료되었습니다.", "정기결제가 ${policy.maxRetryCount}회 모두 실패해 멤버십 혜택이 종료되었습니다.")
                expired++
            } else if (charge(m, now, policy.monthlyPrice, policy.graceDays, CYCLE_MONTHS)) {
                renewed++
            } else {
                failed++
            }
        }

        log.info("멤버십 정기결제 배치 완료: 갱신 {}건 / 실패(재시도대기) {}건 / 만료 {}건", renewed, failed, expired)
        return MembershipBillingRunResult(renewed, failed, expired)
    }

    /** 청구 1건 시도 + 이력 기록 + 상태 전이. 성공하면 true. */
    private fun charge(m: Membership, now: Instant, amount: Long, graceDays: Int, cycleMonths: Long): Boolean {
        val cycleAt = m.nextBillingAt
        val billingKey = billingKeyRepository.findByUserId(m.userId)
        if (billingKey == null) {
            recordFailure(m, cycleAt, now, "등록된 결제수단 없음")
            m.markPastDue(now, graceDays)
            notify(m, "정기결제에 실패했습니다.", "등록된 결제수단이 없어 멤버십 정기결제에 실패했습니다. 카드를 다시 등록해 주세요.")
            return false
        }
        val result = billingKeyGateway.chargeBillingKey(
            ChargeBillingKeyCommand(billingKey.gatewayBillingKey, amount, "MEMBERSHIP-RENEW-${m.id}-$cycleAt"),
        )
        return if (result.success) {
            billingHistoryRepository.save(
                MembershipBillingHistory(
                    membershipId = requireNotNull(m.id),
                    cycleAt = cycleAt,
                    attemptedAt = now,
                    status = BillingHistoryStatus.SUCCESS,
                    gatewayTransactionId = result.transactionId,
                ),
            )
            m.renew(now, cycleMonths)
            true
        } else {
            recordFailure(m, cycleAt, now, result.message)
            m.markPastDue(now, graceDays)
            notify(m, "정기결제에 실패했습니다.", "멤버십 정기결제가 거절되었습니다(${result.message}). ${graceDays}일 내 재시도합니다.")
            false
        }
    }

    private fun recordFailure(m: Membership, cycleAt: Instant, now: Instant, reason: String) {
        billingHistoryRepository.save(
            MembershipBillingHistory(
                membershipId = requireNotNull(m.id),
                cycleAt = cycleAt,
                attemptedAt = now,
                status = BillingHistoryStatus.FAILED,
                failureReason = reason,
            ),
        )
    }

    private fun notify(m: Membership, title: String, body: String) {
        notificationService.notify(m.userId, NotificationType.MEMBERSHIP, title, body)
    }

    fun getHistories(membershipId: Long): List<MembershipBillingHistoryResponse> =
        billingHistoryRepository.findByMembershipIdOrderByIdDesc(membershipId).map { MembershipBillingHistoryResponse.from(it) }

    fun search(condition: AdminMembershipSearchCondition, pageable: Pageable): PageResponse<AdminMembershipResponse> {
        val page = membershipRepository.findPage(pageable) {
            select(entity(Membership::class))
                .from(entity(Membership::class))
                .whereAnd(
                    condition.status?.let { path(Membership::status).eq(it) },
                )
                .orderBy(path(Membership::id).desc())
        }
        return PageResponse.of(page) { AdminMembershipResponse.from(requireNotNull(it)) }
    }

    fun getDetailForAdmin(membershipId: Long): AdminMembershipResponse =
        AdminMembershipResponse.from(
            membershipRepository.findById(membershipId)
                .orElseThrow { BusinessException(ErrorCode.MEMBERSHIP_NOT_FOUND) },
        )

    companion object {
        private const val CYCLE_MONTHS = 1L
    }
}

data class MembershipBillingRunResult(
    val renewed: Int,
    val failed: Int,
    val expired: Int,
)
