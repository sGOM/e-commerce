package com.example.starter.domain.membership

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.billing.gateway.BillingKeyGateway
import com.example.starter.domain.billing.gateway.ChargeBillingKeyCommand
import com.example.starter.domain.billing.gateway.IssueBillingKeyCommand
import com.example.starter.domain.membership.dto.MembershipBillingKeyResponse
import com.example.starter.domain.membership.dto.MembershipResponse
import com.example.starter.domain.membership.dto.RegisterBillingKeyRequest
import com.example.starter.domain.membership.entity.BillingHistoryStatus
import com.example.starter.domain.membership.entity.Membership
import com.example.starter.domain.membership.entity.MembershipBillingHistory
import com.example.starter.domain.membership.entity.MembershipBillingKey
import com.example.starter.domain.membership.entity.MembershipPlan
import com.example.starter.domain.membership.entity.MembershipStatus
import com.example.starter.domain.membership.repository.MembershipBillingHistoryRepository
import com.example.starter.domain.membership.repository.MembershipBillingKeyRepository
import com.example.starter.domain.membership.repository.MembershipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 회원 멤버십 가입/해지/카드등록(회원 전용, 게스트 미지원 — 기획서 §4). 정기결제 실행 자체는
 * [MembershipBillingService](스케줄러/관리자 수동 트리거)가 맡고, 이 서비스는 최초 구독 시 첫 청구만
 * 직접 수행한다("가입 시 즉시 결제" — AC1/AC2: 첫 달 무료 프로모션은 다루지 않으므로 가입 즉시 과금).
 */
@Service
@Transactional(readOnly = true)
class MembershipService(
    private val membershipRepository: MembershipRepository,
    private val billingKeyRepository: MembershipBillingKeyRepository,
    private val billingHistoryRepository: MembershipBillingHistoryRepository,
    private val billingKeyGateway: BillingKeyGateway,
    private val membershipPolicyService: MembershipPolicyService,
    private val membershipBenefitService: MembershipBenefitService,
) {

    /** 카드 등록(빌링키 발급). 이미 등록되어 있으면 교체한다. */
    @Transactional
    fun registerBillingKey(userId: Long, request: RegisterBillingKeyRequest): MembershipBillingKeyResponse {
        val result = billingKeyGateway.issueBillingKey(IssueBillingKeyCommand(userId, requireNotNull(request.cardNumber)))
        if (!result.success) {
            throw BusinessException(ErrorCode.MEMBERSHIP_BILLING_KEY_INVALID, result.message)
        }
        val billingKey = requireNotNull(result.billingKey)
        val cardLast4 = requireNotNull(result.cardLast4)
        val saved = billingKeyRepository.findByUserId(userId)?.also { it.replace(billingKey, cardLast4) }
            ?: billingKeyRepository.save(MembershipBillingKey(userId = userId, gatewayBillingKey = billingKey, cardLast4 = cardLast4))
        return MembershipBillingKeyResponse.from(saved)
    }

    /**
     * 구독 시작(AC1). 첫 청구를 즉시 실행해 성공해야 활성화된다 — 실패하면 멤버십 행을 만들지 않고
     * 결제 거절로 응답한다(주문 결제 실패 시 CREATED 유지와 동일한 원칙, [com.example.starter.domain.payment.PaymentService]).
     */
    @Transactional
    fun subscribe(userId: Long, plan: MembershipPlan): MembershipResponse {
        if (plan != MembershipPlan.BASIC) {
            throw BusinessException(ErrorCode.MEMBERSHIP_PLAN_NOT_SUPPORTED)
        }
        val existing = membershipRepository.findByUserId(userId)
        if (existing != null && existing.status == MembershipStatus.ACTIVE) {
            throw BusinessException(ErrorCode.MEMBERSHIP_ALREADY_ACTIVE)
        }
        val billingKey = billingKeyRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.MEMBERSHIP_BILLING_KEY_NOT_REGISTERED)

        val now = Instant.now()
        val policy = membershipPolicyService.currentPolicy()
        val chargeResult = billingKeyGateway.chargeBillingKey(
            ChargeBillingKeyCommand(billingKey.gatewayBillingKey, policy.monthlyPrice, "MEMBERSHIP-SUBSCRIBE-$userId"),
        )
        if (!chargeResult.success) {
            throw BusinessException(ErrorCode.MEMBERSHIP_SUBSCRIBE_PAYMENT_FAILED, chargeResult.message)
        }

        val membership = existing ?: Membership(userId = userId)
        membership.activate(plan, policy.monthlyPrice, now, CYCLE_MONTHS)
        membershipRepository.save(membership)
        billingHistoryRepository.save(
            MembershipBillingHistory(
                membershipId = requireNotNull(membership.id),
                cycleAt = now,
                attemptedAt = now,
                status = BillingHistoryStatus.SUCCESS,
                gatewayTransactionId = chargeResult.transactionId,
            ),
        )
        return toResponse(membership, now)
    }

    /** 해지 예약(AC3/AC4). 이미 결제한 기간까지는 혜택이 유지되고, 그 이후 스케줄러가 EXPIRED 로 전이한다. */
    @Transactional
    fun cancel(userId: Long): MembershipResponse {
        val membership = membershipRepository.findByUserId(userId)
            ?.takeIf { it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.PAST_DUE }
            ?: throw BusinessException(ErrorCode.MEMBERSHIP_NOT_CANCELABLE)
        membership.scheduleCancel(Instant.now())
        return toResponse(membership, Instant.now())
    }

    fun getMy(userId: Long): MembershipResponse {
        val membership = membershipRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.MEMBERSHIP_NOT_FOUND)
        return toResponse(membership, Instant.now())
    }

    private fun toResponse(membership: Membership, now: Instant): MembershipResponse =
        MembershipResponse.from(membership, membershipBenefitService.benefitSummary(membership.userId, now), now)

    companion object {
        /** 청구 주기(개월). 정책값으로 빼도 되지만 MVP 는 "매월"(AC1) 고정으로 충분하다. */
        const val CYCLE_MONTHS = 1L
    }
}
