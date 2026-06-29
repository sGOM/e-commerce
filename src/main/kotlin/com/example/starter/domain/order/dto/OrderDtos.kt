package com.example.starter.domain.order.dto

import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.OrderItem
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.ShippingAddress
import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

/** 배송지 입력(주문자와 별개의 수령인). */
data class ShippingAddressRequest(
    @field:NotBlank val receiverName: String?,
    @field:NotBlank val receiverPhone: String?,
    @field:NotBlank val zipcode: String?,
    @field:NotBlank val address1: String?,
    val address2: String? = null,
) {
    fun toEmbeddable() = ShippingAddress(
        receiverName = receiverName!!,
        receiverPhone = receiverPhone!!,
        zipcode = zipcode!!,
        address1 = address1!!,
        address2 = address2,
    )
}

/**
 * 주문 생성 요청. 회원의 현재 장바구니 전체를 주문으로 전환한다(항목은 서버가 장바구니에서 가져온다).
 * 쿠폰([issuedCouponId])과 포인트([usePoint])는 선택이며, 할인/사용액은 전부 서버가 계산·검증한다.
 */
data class CreateOrderRequest(
    @field:NotBlank
    val ordererName: String?,
    @field:NotBlank
    val ordererPhone: String?,
    @field:NotBlank
    val ordererEmail: String?,
    @field:Valid
    @field:NotNull
    val shippingAddress: ShippingAddressRequest?,
    val issuedCouponId: Long? = null,
    @field:PositiveOrZero
    val usePoint: Long = 0,
)

/** 게스트 주문 항목(localStorage 동반) */
data class GuestOrderItemRequest(
    @field:NotNull val optionId: Long?,
    @field:NotNull @field:Min(1) val quantity: Int?,
)

/**
 * 게스트(비회원) 주문 생성 요청. 장바구니가 없으므로 항목을 직접 전달한다.
 * 쿠폰/포인트는 회원 전용이라 받지 않는다.
 */
data class GuestOrderRequest(
    @field:NotBlank val ordererName: String?,
    @field:NotBlank val ordererPhone: String?,
    @field:NotBlank val ordererEmail: String?,
    @field:Valid @field:NotNull val shippingAddress: ShippingAddressRequest?,
    @field:Valid @field:NotEmpty val items: List<GuestOrderItemRequest> = emptyList(),
)

/** 게스트 주문 조회 요청 — 주문번호 + 연락처 검증 */
data class GuestOrderLookupRequest(
    @field:NotBlank val orderNumber: String?,
    @field:NotBlank val ordererPhone: String?,
)

/** 게스트 주문을 회원 계정에 연결(claim) 요청 — 주문번호 + 연락처로 본인 확인 */
data class ClaimGuestOrderRequest(
    @field:NotBlank val orderNumber: String?,
    @field:NotBlank val ordererPhone: String?,
)

/** 주문 항목(스냅샷) 응답 */
data class OrderItemResponse(
    val optionId: Long,
    val productName: String,
    val optionName: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
) {
    companion object {
        fun from(item: OrderItem) = OrderItemResponse(
            optionId = item.optionId,
            productName = item.productName,
            optionName = item.optionName,
            unitPrice = item.unitPrice,
            quantity = item.quantity,
            lineTotal = item.lineTotal,
        )
    }
}

/** 하위 주문(판매자 단위) 응답 */
data class SubOrderResponse(
    val subOrderId: Long,
    val sellerId: Long,
    val storeName: String,
    val status: SubOrderStatus,
    val subtotal: Long,
    val items: List<OrderItemResponse>,
) {
    companion object {
        fun from(subOrder: SubOrder) = SubOrderResponse(
            subOrderId = requireNotNull(subOrder.id),
            sellerId = requireNotNull(subOrder.seller.id),
            storeName = subOrder.seller.storeName,
            status = subOrder.status,
            subtotal = subOrder.subtotal,
            items = subOrder.items.map { OrderItemResponse.from(it) },
        )
    }
}

/** 배송지 응답 */
data class ShippingAddressResponse(
    val receiverName: String,
    val receiverPhone: String,
    val zipcode: String,
    val address1: String,
    val address2: String?,
) {
    companion object {
        fun from(a: ShippingAddress) = ShippingAddressResponse(
            receiverName = a.receiverName,
            receiverPhone = a.receiverPhone,
            zipcode = a.zipcode,
            address1 = a.address1,
            address2 = a.address2,
        )
    }
}

/** 주문 상세 응답 */
data class OrderResponse(
    val orderId: Long,
    val orderNumber: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val discountAmount: Long,
    val pointUsed: Long,
    val payableAmount: Long,
    val ordererName: String,
    val shippingAddress: ShippingAddressResponse,
    val createdAt: Instant,
    val subOrders: List<SubOrderResponse>,
) {
    companion object {
        fun from(order: Order) = OrderResponse(
            orderId = requireNotNull(order.id),
            orderNumber = order.orderNumber,
            status = order.status,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            pointUsed = order.pointUsed,
            payableAmount = order.payableAmount,
            ordererName = order.ordererName,
            shippingAddress = ShippingAddressResponse.from(order.shippingAddress),
            createdAt = order.createdAt,
            subOrders = order.subOrders
                .sortedBy { it.id }
                .map { SubOrderResponse.from(it) },
        )
    }
}

/** 주문 목록(헤더) 응답 */
data class OrderSummaryResponse(
    val orderId: Long,
    val orderNumber: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val payableAmount: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(order: Order) = OrderSummaryResponse(
            orderId = requireNotNull(order.id),
            orderNumber = order.orderNumber,
            status = order.status,
            totalAmount = order.totalAmount,
            payableAmount = order.payableAmount,
            createdAt = order.createdAt,
        )
    }
}
