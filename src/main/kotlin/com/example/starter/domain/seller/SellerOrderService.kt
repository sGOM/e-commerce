package com.example.starter.domain.seller

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.seller.dto.SellerSubOrderResponse
import com.example.starter.domain.seller.repository.SellerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 판매자 주문(SubOrder) 처리. 본인 판매분만 조회·발송할 수 있다(소유권 격리).
 */
@Service
@Transactional(readOnly = true)
class SellerOrderService(
    private val sellerRepository: SellerRepository,
    private val subOrderRepository: SubOrderRepository,
) {

    fun getMySubOrders(userId: Long, status: SubOrderStatus?): List<SellerSubOrderResponse> {
        val sellerId = sellerId(userId)
        val subOrders = if (status == null) {
            subOrderRepository.findBySellerIdOrderByIdDesc(sellerId)
        } else {
            subOrderRepository.findBySellerIdAndStatusOrderByIdDesc(sellerId, status)
        }
        return subOrders.map { SellerSubOrderResponse.from(it) }
    }

    /** 본인 판매분 SubOrder 에 송장을 등록하고 SHIPPED 로 전이한다. */
    @Transactional
    fun ship(userId: Long, subOrderId: Long, courier: String, trackingNumber: String): SellerSubOrderResponse {
        val subOrder = findOwnedSubOrder(userId, subOrderId)
        if (!subOrder.isShippable) {
            throw BusinessException(ErrorCode.SUB_ORDER_NOT_SHIPPABLE)
        }
        subOrder.ship(courier, trackingNumber)
        return SellerSubOrderResponse.from(subOrder)
    }

    private fun findOwnedSubOrder(userId: Long, subOrderId: Long): SubOrder =
        subOrderRepository.findWithDetailsByIdAndSellerId(subOrderId, sellerId(userId))
            .orElseThrow { BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) }

    private fun sellerId(userId: Long): Long =
        (sellerRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)).id!!
}
