package com.example.starter.domain.settlement

import com.example.starter.domain.settlement.dto.SettlementPolicyResponse
import com.example.starter.domain.settlement.entity.SettlementPolicy
import com.example.starter.domain.settlement.repository.SettlementPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 플랫폼 수수료 정책 관리(관리자). 단일 정책 행의 수수료율을 런타임 변경한다.
 */
@Service
@Transactional(readOnly = true)
class SettlementPolicyService(
    private val settlementPolicyRepository: SettlementPolicyRepository,
) {

    fun getPolicy(): SettlementPolicyResponse = SettlementPolicyResponse(currentPolicy().commissionRateBp)

    fun currentRateBp(): Int = currentPolicy().commissionRateBp

    @Transactional
    fun update(commissionRateBp: Int): SettlementPolicyResponse {
        require(commissionRateBp in 0..10_000) { "수수료율은 0~10000bp 사이여야 합니다." }
        val policy = currentPolicy()
        policy.commissionRateBp = commissionRateBp
        return SettlementPolicyResponse(policy.commissionRateBp)
    }

    /** 마이그레이션이 1행을 시드하지만, 부재 시 기본 10%로 생성해 NPE 를 막는다. */
    private fun currentPolicy(): SettlementPolicy =
        settlementPolicyRepository.findFirstByOrderByIdAsc()
            ?: settlementPolicyRepository.save(SettlementPolicy(commissionRateBp = 1000))
}
