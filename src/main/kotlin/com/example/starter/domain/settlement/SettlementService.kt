package com.example.starter.domain.settlement

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.settlement.dto.SettlementResponse
import com.example.starter.domain.settlement.entity.Settlement
import com.example.starter.domain.settlement.entity.SettlementStatus
import com.example.starter.domain.settlement.repository.SettlementRepository
import org.springframework.data.domain.Pageable
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
        // 반품 완료 건은 판매자가 받을 반품 배송비가 있을 때만(불량·오배송 반품은 판매자 부담이라 줄 돈이 없다)
        val targets = subOrderRepository.findBySettlementIdIsNullAndStatusIn(SETTLEABLE)
            .filter { it.status != SubOrderStatus.RETURNED || it.sellerReturnFee > 0 }
        return targets.groupBy { requireNotNull(it.seller.id) }.map { (_, subOrders) ->
            val sales = subOrders.sumOf { it.settlementSales }
            val commission = sales * rateBp / 10_000
            val deliveryFee = subOrders.sumOf { it.settlementDeliveryFee } // 수수료 없음(7.3)
            val settlement = settlementRepository.save(
                Settlement(
                    seller = subOrders.first().seller,
                    salesAmount = sales,
                    commissionAmount = commission,
                    deliveryFeeAmount = deliveryFee,
                    payoutAmount = sales - commission + deliveryFee,
                    settledCount = subOrders.size,
                ),
            )
            subOrders.forEach { it.settlementId = settlement.id } // 정산 표시(재정산 방지)
            SettlementResponse.from(settlement)
        }
    }

    fun getSellerSettlements(userId: Long): List<SettlementResponse> =
        settlementRepository.findBySellerIdOrderByIdDesc(sellerId(userId)).map { SettlementResponse.from(it) }

    /** 전체 정산 목록(관리자). 최신순, 상태 필터는 선택. */
    fun getAllSettlements(status: SettlementStatus?, pageable: Pageable): PageResponse<SettlementResponse> {
        val page = if (status == null) {
            settlementRepository.findAllByOrderByIdDesc(pageable)
        } else {
            settlementRepository.findByStatusOrderByIdDesc(status, pageable)
        }
        return PageResponse.of(page) { SettlementResponse.from(it) }
    }

    /** 판매대금 지급 완료 처리(관리자). */
    @Transactional
    fun pay(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findById(settlementId)
            .orElseThrow { BusinessException(ErrorCode.SETTLEMENT_NOT_FOUND) }
        settlement.markPaid()
        return SettlementResponse.from(settlement)
    }

    private fun sellerId(userId: Long): Long =
        (
            sellerRepository.findByUserId(userId)
                ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)
            ).id!!

    companion object {
        // 취소·반품 진행 중을 제외한 결제 완료 이후 상태 + 반품 완료(반품 배송비 정산용)를 대상으로 본다.
        private val SETTLEABLE = listOf(
            SubOrderStatus.PAID,
            SubOrderStatus.PREPARING,
            SubOrderStatus.SHIPPED,
            SubOrderStatus.DELIVERED,
            SubOrderStatus.RETURNED,
        )
    }
}
