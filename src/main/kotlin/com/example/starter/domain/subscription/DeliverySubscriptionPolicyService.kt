package com.example.starter.domain.subscription

import com.example.starter.domain.subscription.dto.DeliverySubscriptionPolicyResponse
import com.example.starter.domain.subscription.dto.UpdateDeliverySubscriptionPolicyRequest
import com.example.starter.domain.subscription.entity.DeliverySubscriptionPolicy
import com.example.starter.domain.subscription.repository.DeliverySubscriptionPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 정기배송 정책 관리(관리자). [com.example.starter.domain.membership.MembershipPolicyService] 와
 * 동일 패턴(단일 정책 행, 전달한 항목만 변경).
 */
@Service
@Transactional(readOnly = true)
class DeliverySubscriptionPolicyService(
    private val policyRepository: DeliverySubscriptionPolicyRepository,
) {

    fun getPolicy(): DeliverySubscriptionPolicyResponse = currentPolicy().toResponse()

    @Transactional
    fun update(request: UpdateDeliverySubscriptionPolicyRequest): DeliverySubscriptionPolicyResponse {
        val policy = currentPolicy()
        request.maxConsecutiveFailures?.let {
            require(it >= 1) { "최대 연속 실패 허용 횟수는 1 이상이어야 합니다." }
            policy.maxConsecutiveFailures = it
        }
        request.skipDeadlineDays?.let {
            require(it >= 0) { "스킵 마감일은 0 이상이어야 합니다." }
            policy.skipDeadlineDays = it
        }
        return policy.toResponse()
    }

    internal fun currentPolicy(): DeliverySubscriptionPolicy =
        policyRepository.findFirstByOrderByIdAsc() ?: policyRepository.save(DeliverySubscriptionPolicy())

    private fun DeliverySubscriptionPolicy.toResponse() = DeliverySubscriptionPolicyResponse(
        maxConsecutiveFailures = maxConsecutiveFailures,
        skipDeadlineDays = skipDeadlineDays,
    )
}
