package com.example.starter.domain.seller.dto

import com.example.starter.domain.order.dto.OrderItemResponse
import com.example.starter.domain.order.entity.Shipment
import com.example.starter.domain.order.entity.ShipmentStatus
import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant

/** 송장 등록 요청 */
data class ShipRequest(
    @field:NotBlank val courier: String?,
    @field:NotBlank val trackingNumber: String?,
)

/** 배송 응답 */
data class ShipmentResponse(
    val courier: String,
    val trackingNumber: String,
    val status: ShipmentStatus,
    val shippedAt: Instant?,
) {
    companion object {
        fun from(s: Shipment) = ShipmentResponse(
            courier = s.courier,
            trackingNumber = s.trackingNumber,
            status = s.status,
            shippedAt = s.shippedAt,
        )
    }
}

/** 판매자용 하위 주문 응답(본인 판매분) */
data class SellerSubOrderResponse(
    val subOrderId: Long,
    val orderNumber: String,
    val status: SubOrderStatus,
    val subtotal: Long,
    val items: List<OrderItemResponse>,
    val shipment: ShipmentResponse?,
) {
    companion object {
        fun from(subOrder: SubOrder) = SellerSubOrderResponse(
            subOrderId = requireNotNull(subOrder.id),
            orderNumber = subOrder.order.orderNumber,
            status = subOrder.status,
            subtotal = subOrder.subtotal,
            items = subOrder.items.map { OrderItemResponse.from(it) },
            shipment = subOrder.shipment?.let { ShipmentResponse.from(it) },
        )
    }
}
