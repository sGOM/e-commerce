package com.example.starter.domain.settlement

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.settlement.dto.SellerDashboardResponse
import com.example.starter.domain.settlement.entity.SettlementStatus
import com.example.starter.domain.settlement.repository.SettlementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId

/**
 * 판매자 매출 대시보드. 기간은 한국 시간 날짜 기준이며 생략하면 오늘까지 최근 30일이다.
 */
@Service
@Transactional(readOnly = true)
class SellerDashboardService(
    private val subOrderRepository: SubOrderRepository,
    private val settlementRepository: SettlementRepository,
    private val sellerRepository: SellerRepository,
) {

    fun get(userId: Long, from: LocalDate?, to: LocalDate?): SellerDashboardResponse {
        val sellerId = (sellerRepository.findByUserId(userId) ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)).id!!
        val end = to ?: LocalDate.now(ZONE)
        val start = from ?: end.minusDays(29)
        val sales = subOrderRepository.summarizeSales(
            sellerId,
            SOLD,
            start.atStartOfDay(ZONE).toInstant(),
            end.plusDays(1).atStartOfDay(ZONE).toInstant(),
        )
        return SellerDashboardResponse(
            from = start,
            to = end,
            orderCount = sales.orderCount,
            salesAmount = sales.salesAmount,
            unsettledAmount = subOrderRepository.sumUnsettledSubtotal(sellerId, SOLD),
            pendingPayoutAmount = settlementRepository.sumPayoutAmount(sellerId, SettlementStatus.PENDING),
        )
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        // 정산 대상과 같은 기준: 결제 이후 상태, 취소 제외
        private val SOLD = listOf(
            SubOrderStatus.PAID,
            SubOrderStatus.PREPARING,
            SubOrderStatus.SHIPPED,
            SubOrderStatus.DELIVERED,
        )
    }
}
