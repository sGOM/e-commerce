package com.example.starter.domain.point

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.membership.MembershipBenefitService
import com.example.starter.domain.point.dto.PointSummaryResponse
import com.example.starter.domain.point.entity.PointAccount
import com.example.starter.domain.point.repository.PointAccountRepository
import com.example.starter.domain.point.repository.PointPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 회원 포인트. 사용(주문 시 차감)·적립(결제 확정 시, 만료일 부여)·환원(취소/실패)·만료를 lot 원장 기반으로 처리한다.
 * 적립률·유효기간은 [PointPolicy](관리자 설정)를 따른다. 게스트는 포인트를 쓸 수 없다(회원 전용).
 */
@Service
@Transactional(readOnly = true)
class PointService(
    private val pointAccountRepository: PointAccountRepository,
    private val pointPolicyRepository: PointPolicyRepository,
    private val membershipBenefitService: MembershipBenefitService,
) {

    /** 주문에 포인트 사용(차감). 결제금액 초과/잔액 부족 시 예외로 주문 트랜잭션을 롤백. */
    @Transactional
    fun use(userId: Long, amount: Long, orderId: Long, maxUsable: Long) {
        if (amount <= 0) return
        if (amount > maxUsable) {
            throw BusinessException(ErrorCode.POINT_EXCEEDS_PAYABLE)
        }
        val account = getOrCreateAccount(userId)
        if (!account.use(amount, orderId)) {
            throw BusinessException(ErrorCode.INSUFFICIENT_POINT)
        }
    }

    /**
     * 결제 확정 적립. 정책 적립률을 결제금액에 적용하고 유효기간 만료일을 부여한다. 적립액을 반환.
     * 멤버십 활성 회원은 우대 배율(예: 1.5배)이 추가로 곱해진다(`docs/planning/subscription-membership.md` AC9).
     */
    @Transactional
    fun earn(userId: Long, payableAmount: Long, orderId: Long): Long {
        val policy = pointPolicyRepository.findFirstByOrderByIdAsc() ?: return 0
        val baseAmount = policy.calculateEarn(payableAmount)
        if (baseAmount <= 0) return 0
        val multiplierBp = membershipBenefitService.pointEarnMultiplierBp(userId)
        val amount = baseAmount * multiplierBp / 10_000
        if (amount <= 0) return 0
        getOrCreateAccount(userId).earn(amount, orderId, policy.expiresAtFrom(Instant.now()))
        return amount
    }

    /**
     * 리뷰 작성 적립([review] 도메인 전용). 결제 확정 적립([earn])과 달리 특정 주문에 연결하지 않는다
     * (`sourceOrderId = null`) — 리뷰가 달린 주문이 이후 취소되어도 [revokeEarnForOrder] 로 회수되지
     * 않게 하기 위함(기획서 AC7: 삭제해도 회수하지 않음 — 애초에 주문 취소 회수 대상도 아니다).
     */
    @Transactional
    fun earnForReview(userId: Long, amount: Long): Long {
        if (amount <= 0) return 0
        getOrCreateAccount(userId).earn(amount, orderId = null, expiresAtByPolicy())
        return amount
    }

    /** 사용 포인트 환원(취소/결제 실패). 새 lot 으로 적립하며 만료일을 재부여한다. */
    @Transactional
    fun restoreUse(userId: Long, amount: Long, orderId: Long) {
        if (amount <= 0) return
        val expiresAt = expiresAtByPolicy()
        pointAccountRepository.findWithLotsByUserId(userId).ifPresent { it.cancelUse(amount, orderId, expiresAt) }
    }

    /** 해당 주문으로 적립된 lot 의 잔여를 회수(결제 취소/환불). */
    @Transactional
    fun revokeEarnForOrder(userId: Long, orderId: Long) {
        pointAccountRepository.findWithLotsByUserId(userId).ifPresent { it.revokeEarnByOrder(orderId) }
    }

    /** 만료일이 지난 모든 적립 lot 을 소멸 처리한다(스케줄러/관리자 트리거). 만료된 총량 반환. */
    @Transactional
    fun expireDuePoints(): Long {
        val now = Instant.now()
        return pointAccountRepository.findAll().sumOf { it.expireDue(now) }
    }

    fun getMyPoints(userId: Long): PointSummaryResponse =
        PointSummaryResponse.from(pointAccountRepository.findWithTransactionsByUserId(userId).orElse(null))

    private fun expiresAtByPolicy(): Instant {
        val policy = pointPolicyRepository.findFirstByOrderByIdAsc()
        return policy?.expiresAtFrom(Instant.now())
            ?: Instant.now().plus(DEFAULT_EXPIRY_DAYS, java.time.temporal.ChronoUnit.DAYS)
    }

    private fun getOrCreateAccount(userId: Long): PointAccount =
        pointAccountRepository.findWithLotsByUserId(userId)
            .orElseGet { pointAccountRepository.save(PointAccount(userId = userId)) }

    companion object {
        private const val DEFAULT_EXPIRY_DAYS = 365L
    }
}
