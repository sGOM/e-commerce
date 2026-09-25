package com.example.starter.domain.order.tracking

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.dto.TrackingResponse
import com.example.starter.domain.order.repository.SubOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 구매자 배송 조회(ROADMAP 6.2). 본인 주문의 발송된 하위 주문만 조회한다. */
@Service
@Transactional(readOnly = true)
class DeliveryTrackingService(
    private val subOrderRepository: SubOrderRepository,
    private val deliveryTracker: DeliveryTracker,
) {

    fun track(userId: Long, subOrderId: Long): TrackingResponse {
        val subOrder = subOrderRepository.findById(subOrderId)
            .filter { it.order.userId == userId } // 남의 주문이면 존재를 숨긴다
            .orElseThrow { BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) }
        val shipment = subOrder.shipment ?: throw BusinessException(ErrorCode.SHIPMENT_NOT_FOUND)
        val result = deliveryTracker.track(shipment.courier, shipment.trackingNumber)
        return TrackingResponse(
            courier = shipment.courier,
            trackingNumber = shipment.trackingNumber,
            supported = result != null,
            delivered = result?.delivered ?: false,
            events = result?.events.orEmpty(),
        )
    }
}
