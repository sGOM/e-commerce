package com.example.starter.domain.membership.dto

import com.example.starter.domain.membership.entity.Membership
import com.example.starter.domain.membership.entity.MembershipBillingHistory
import com.example.starter.domain.membership.entity.MembershipBillingKey
import com.example.starter.domain.membership.entity.MembershipPlan
import com.example.starter.domain.membership.entity.MembershipStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

/** 빌링키(카드) 등록/재등록 요청. Mock 게이트웨이는 형식만 검증하고 실 카드 통신은 하지 않는다. */
data class RegisterBillingKeyRequest(
    @field:NotBlank
    @field:Pattern(regexp = "\\d{12,16}", message = "카드번호는 숫자 12~16자리여야 합니다.")
    val cardNumber: String?,
)

data class MembershipBillingKeyResponse(
    val cardLast4: String,
    val registeredAt: Instant,
) {
    companion object {
        fun from(key: MembershipBillingKey) = MembershipBillingKeyResponse(
            cardLast4 = key.cardLast4,
            registeredAt = key.createdAt,
        )
    }
}

/** 구독 시작 요청. 플랜 생략 시 BASIC(현재 유일 판매 플랜). */
data class SubscribeMembershipRequest(
    val plan: MembershipPlan = MembershipPlan.BASIC,
)

/** 내 멤버십 요약 — 상태/다음 결제일과 함께 지금 적용되는 혜택을 응답에 그대로 노출한다(체크아웃 안내용). */
data class MembershipResponse(
    val id: Long,
    val plan: MembershipPlan,
    val status: MembershipStatus,
    val price: Long,
    val startAt: Instant,
    val nextBillingAt: Instant,
    val canceledAt: Instant?,
    val benefitActive: Boolean,
    val benefits: MembershipBenefitSummary,
) {
    companion object {
        fun from(membership: Membership, benefits: MembershipBenefitSummary, now: Instant = Instant.now()) = MembershipResponse(
            id = requireNotNull(membership.id),
            plan = membership.plan,
            status = membership.status,
            price = membership.price,
            startAt = membership.startAt,
            nextBillingAt = membership.nextBillingAt,
            canceledAt = membership.canceledAt,
            benefitActive = membership.isBenefitActive(now),
            benefits = benefits,
        )
    }
}

/** 지금 적용 중인 혜택 값(체크아웃 인라인 안내에 그대로 쓸 수 있는 형태). */
data class MembershipBenefitSummary(
    val freeShipping: Boolean,
    val pointEarnMultiplierBp: Int,
)

data class MembershipBillingHistoryResponse(
    val id: Long,
    val cycleAt: Instant,
    val attemptedAt: Instant,
    val status: String,
    val failureReason: String?,
) {
    companion object {
        fun from(history: MembershipBillingHistory) = MembershipBillingHistoryResponse(
            id = requireNotNull(history.id),
            cycleAt = history.cycleAt,
            attemptedAt = history.attemptedAt,
            status = history.status.name,
            failureReason = history.failureReason,
        )
    }
}

/** 관리자 구독 현황 검색 조건. */
data class AdminMembershipSearchCondition(
    val status: MembershipStatus? = null,
)

/** 관리자 구독 현황 응답 — 회원 혜택 요약 없이 운영에 필요한 상태 정보만 노출한다. */
data class AdminMembershipResponse(
    val id: Long,
    val userId: Long,
    val plan: MembershipPlan,
    val status: MembershipStatus,
    val price: Long,
    val startAt: Instant,
    val nextBillingAt: Instant,
    val canceledAt: Instant?,
    val billingFailureCount: Int,
    val gracePeriodEndsAt: Instant?,
) {
    companion object {
        fun from(membership: Membership) = AdminMembershipResponse(
            id = requireNotNull(membership.id),
            userId = membership.userId,
            plan = membership.plan,
            status = membership.status,
            price = membership.price,
            startAt = membership.startAt,
            nextBillingAt = membership.nextBillingAt,
            canceledAt = membership.canceledAt,
            billingFailureCount = membership.billingFailureCount,
            gracePeriodEndsAt = membership.gracePeriodEndsAt,
        )
    }
}

data class MembershipPolicyResponse(
    val monthlyPrice: Long,
    val pointEarnMultiplierBp: Int,
    val freeShippingEnabled: Boolean,
    val maxRetryCount: Int,
    val graceDays: Int,
)

/** 정책 부분 업데이트 요청(전달한 항목만 변경). */
data class UpdateMembershipPolicyRequest(
    val monthlyPrice: Long? = null,
    val pointEarnMultiplierBp: Int? = null,
    val freeShippingEnabled: Boolean? = null,
    val maxRetryCount: Int? = null,
    val graceDays: Int? = null,
)
