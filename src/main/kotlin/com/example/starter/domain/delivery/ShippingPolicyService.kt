package com.example.starter.domain.delivery

import com.example.starter.domain.delivery.dto.ShippingPolicyResponse
import com.example.starter.domain.delivery.dto.UpdateShippingPolicyRequest
import com.example.starter.domain.delivery.entity.ShippingPolicy
import com.example.starter.domain.delivery.repository.ShippingPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 기본 배송비 정책 조회·변경. 금액은 코드 상수가 아니라 DB 정책 행이다. */
@Service
@Transactional(readOnly = true)
class ShippingPolicyService(
    private val shippingPolicyRepository: ShippingPolicyRepository,
) {

    fun getPolicy(): ShippingPolicyResponse = currentPolicy().let {
        ShippingPolicyResponse(baseFee = it.baseFee, returnFee = it.returnFee, returnWindowDays = it.returnWindowDays)
    }

    fun baseFee(): Long = currentPolicy().baseFee

    /** 단순변심 반품 시 환불액에서 차감하는 반품 배송비(왕복). */
    fun returnFee(): Long = currentPolicy().returnFee

    /** 구매확정(DELIVERED) 후 반품을 요청할 수 있는 일수. */
    fun returnWindowDays(): Int = currentPolicy().returnWindowDays

    @Transactional
    fun update(request: UpdateShippingPolicyRequest): ShippingPolicyResponse {
        val policy = currentPolicy()
        request.baseFee?.let { policy.baseFee = it }
        request.returnFee?.let { policy.returnFee = it }
        request.returnWindowDays?.let { policy.returnWindowDays = it }
        return getPolicy()
    }

    /** 마이그레이션이 1행을 시드하지만, 부재 시 기본값(3,000원)으로 생성한다. */
    private fun currentPolicy(): ShippingPolicy =
        shippingPolicyRepository.findFirstByOrderByIdAsc()
            ?: shippingPolicyRepository.save(ShippingPolicy(baseFee = 3_000))
}
