package com.example.starter.domain.point

import com.example.starter.domain.point.dto.PointPolicyResponse
import com.example.starter.domain.point.entity.PointPolicy
import com.example.starter.domain.point.repository.PointPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 포인트 적립 정책 관리(관리자). 단일 정책 행의 적립률([PointPolicy.earnRateBp])을 런타임 변경한다.
 */
@Service
@Transactional(readOnly = true)
class PointPolicyService(
    private val pointPolicyRepository: PointPolicyRepository,
) {

    fun getPolicy(): PointPolicyResponse = currentPolicy().toResponse()

    /** 전달한 항목만 변경한다(부분 업데이트). */
    @Transactional
    fun update(earnRateBp: Int?, expiryDays: Int?): PointPolicyResponse {
        val policy = currentPolicy()
        earnRateBp?.let {
            require(it >= 0) { "적립률은 0 이상이어야 합니다." }
            policy.earnRateBp = it
        }
        expiryDays?.let {
            require(it >= 1) { "유효기간은 1일 이상이어야 합니다." }
            policy.expiryDays = it
        }
        return policy.toResponse()
    }

    private fun PointPolicy.toResponse() = PointPolicyResponse(earnRateBp = earnRateBp, expiryDays = expiryDays)

    /** 마이그레이션이 1행을 시드하지만, 부재 시 기본값으로 생성해 NPE 를 막는다. */
    private fun currentPolicy(): PointPolicy =
        pointPolicyRepository.findFirstByOrderByIdAsc()
            ?: pointPolicyRepository.save(PointPolicy(earnRateBp = 0))
}
