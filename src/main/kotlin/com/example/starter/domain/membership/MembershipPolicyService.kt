package com.example.starter.domain.membership

import com.example.starter.domain.membership.dto.MembershipPolicyResponse
import com.example.starter.domain.membership.dto.UpdateMembershipPolicyRequest
import com.example.starter.domain.membership.entity.MembershipPolicy
import com.example.starter.domain.membership.repository.MembershipPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 멤버십 정책 관리(관리자). 단일 정책 행(월 구독료/포인트 우대 배율/무료배송 여부/재시도·유예 설정)을
 * 런타임 변경한다([PointPolicyService]/[com.example.starter.domain.settlement.SettlementPolicyService] 와 동일 패턴).
 */
@Service
@Transactional(readOnly = true)
class MembershipPolicyService(
    private val membershipPolicyRepository: MembershipPolicyRepository,
) {

    fun getPolicy(): MembershipPolicyResponse = currentPolicy().toResponse()

    /** 전달한 항목만 변경한다(부분 업데이트). */
    @Transactional
    fun update(request: UpdateMembershipPolicyRequest): MembershipPolicyResponse {
        val policy = currentPolicy()
        request.monthlyPrice?.let {
            require(it >= 0) { "월 구독료는 0 이상이어야 합니다." }
            policy.monthlyPrice = it
        }
        request.pointEarnMultiplierBp?.let {
            require(it >= 0) { "포인트 적립 배율은 0 이상이어야 합니다." }
            policy.pointEarnMultiplierBp = it
        }
        request.freeShippingEnabled?.let { policy.freeShippingEnabled = it }
        request.maxRetryCount?.let {
            require(it >= 1) { "최대 재시도 횟수는 1 이상이어야 합니다." }
            policy.maxRetryCount = it
        }
        request.graceDays?.let {
            require(it >= 0) { "유예기간은 0 이상이어야 합니다." }
            policy.graceDays = it
        }
        return policy.toResponse()
    }

    internal fun currentPolicy(): MembershipPolicy =
        membershipPolicyRepository.findFirstByOrderByIdAsc()
            ?: membershipPolicyRepository.save(MembershipPolicy(monthlyPrice = 0))

    private fun MembershipPolicy.toResponse() = MembershipPolicyResponse(
        monthlyPrice = monthlyPrice,
        pointEarnMultiplierBp = pointEarnMultiplierBp,
        freeShippingEnabled = freeShippingEnabled,
        maxRetryCount = maxRetryCount,
        graceDays = graceDays,
    )
}
