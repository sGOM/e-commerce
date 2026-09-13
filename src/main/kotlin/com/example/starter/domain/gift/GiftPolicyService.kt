package com.example.starter.domain.gift

import com.example.starter.domain.gift.dto.GiftPolicyResponse
import com.example.starter.domain.gift.entity.GiftPolicy
import com.example.starter.domain.gift.repository.GiftPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 선물 링크 만료기한 정책 관리(관리자). 단일 정책 행([GiftPolicy.expiryDays])을 런타임 변경한다
 * ([com.example.starter.domain.point.PointPolicyService] 와 동일 패턴).
 */
@Service
@Transactional(readOnly = true)
class GiftPolicyService(
    private val giftPolicyRepository: GiftPolicyRepository,
) {

    fun getPolicy(): GiftPolicyResponse = currentPolicy().toResponse()

    @Transactional
    fun update(expiryDays: Int?): GiftPolicyResponse {
        val policy = currentPolicy()
        expiryDays?.let {
            require(it >= 1) { "만료기한은 1일 이상이어야 합니다." }
            policy.expiryDays = it
        }
        return policy.toResponse()
    }

    private fun GiftPolicy.toResponse() = GiftPolicyResponse(expiryDays = expiryDays)

    private fun currentPolicy(): GiftPolicy =
        giftPolicyRepository.findFirstByOrderByIdAsc() ?: giftPolicyRepository.save(GiftPolicy())
}
