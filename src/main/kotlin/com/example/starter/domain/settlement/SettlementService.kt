package com.example.starter.domain.settlement

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.settlement.dto.SettlementResponse
import com.example.starter.domain.settlement.entity.Settlement
import com.example.starter.domain.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 셀러 정산. 미정산 SubOrder(취소 제외)를 판매자 단위로 집계해 정산서를 만들고,
 * 판매액에서 플랫폼 수수료를 차감해 지급액을 산정한다. 같은 SubOrder 는 한 번만 정산된다(settlementId 로 표시).
 */
@Service
@Transactional(readOnly = true)
class SettlementService(
    private val settlementRepository: SettlementRepository,
    private val subOrderRepository: SubOrderRepository,
    private val sellerRepository: SellerRepository,
    private val settlementPolicyService: SettlementPolicyService,
) {

    /** 미정산 대상을 판매자별로 모아 정산서를 생성한다(관리자). */
    @Transactional
    fun generate(): List<SettlementResponse> {
        val rateBp = settlementPolicyService.currentRateBp()
        val targets = subOrderRepository.findBySettlementIdIsNullAndStatusIn(SETTLEABLE)
        return targets.groupBy { requireNotNull(it.seller.id) }.map { (_, subOrders) ->
            val sales = subOrders.sumOf { it.subtotal }
            val commission = sales * rateBp / 10_000
            val settlement = settlementRepository.save(
                Settlement(
                    seller = subOrders.first().seller,
                    salesAmount = sales,
                    commissionAmount = commission,
                    payoutAmount = sales - commission,
                    settledCount = subOrders.size,
                ),
            )
            subOrders.forEach { it.settlementId = settlement.id } // 정산 표시(재정산 방지)
            SettlementResponse.from(settlement)
        }
    }

    fun getSellerSettlements(userId: Long): List<SettlementResponse> =
        settlementRepository.findBySellerIdOrderByIdDesc(sellerId(userId)).map { SettlementResponse.from(it) }

    /** 판매대금 지급 완료 처리(관리자). */
    @Transactional
    fun pay(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findById(settlementId)
            .orElseThrow { BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND) }
        settlement.markPaid()
        return SettlementResponse.from(settlement)
    }

    private fun sellerId(userId: Long): Long =
        (sellerRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)).id!!

    companion object {
        // 취소를 제외한 결제 완료 이후 상태를 정산 대상으로 본다.
        private val SETTLEABLE = listOf(
            SubOrderStatus.PAID,
            SubOrderStatus.PREPARING,
            SubOrderStatus.SHIPPED,
            SubOrderStatus.DELIVERED,
        )
    }
}
