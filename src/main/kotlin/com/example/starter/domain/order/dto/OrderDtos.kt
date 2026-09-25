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
import jakarta.validation.constraints.Size
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
 * 배송 슬롯 선택(SubOrder 단위, `docs/planning/delivery-slot.md` AC6). [sellerId] 는 주문이 판매자
 * 단위로 분리되기 전 시점에 클라이언트가 지정하는 대상 키다(서버가 장바구니/게스트 항목을 판매자별로
 * 묶어 SubOrder 를 만들 때 이 값으로 매칭한다). 새벽배송 가능 상품이 없는 SubOrder 에는 적용할 수 없다.
 */
data class DeliverySlotSelectionRequest(
    @field:NotNull val sellerId: Long?,
    @field:NotNull val deliverySlotId: Long?,
)

/**
 * 주문 생성 요청. 회원의 현재 장바구니 전체를 주문으로 전환한다(항목은 서버가 장바구니에서 가져온다).
 * 쿠폰([issuedCouponId])과 포인트([usePoint])는 선택이며, 할인/사용액은 전부 서버가 계산·검증한다.
 *
 * 선물 주문([isGift] = true, `docs/planning/gift-order.md`)은 [shippingAddress] 를 생략할 수 있다 —
 * 수령자가 나중에 링크로 입력한다(서비스 레이어에서 검증: 일반 주문은 배송지가 필수,
 * [com.example.starter.domain.order.OrderService.createFromCart]). 배송 슬롯은 배송지 기반 검증이
 * 필요해 선물 주문에서는 지원하지 않는다(비어 있지 않으면 거부).
 */
data class CreateOrderRequest(
    @field:NotBlank
    val ordererName: String?,
    @field:NotBlank
    val ordererPhone: String?,
    @field:NotBlank
    val ordererEmail: String?,
    @field:Valid
    val shippingAddress: ShippingAddressRequest? = null,
    val issuedCouponId: Long? = null,
    @field:PositiveOrZero
    val usePoint: Long = 0,
    @field:Valid
    val deliverySlotSelections: List<DeliverySlotSelectionRequest> = emptyList(),
    val isGift: Boolean = false,
    @field:Size(max = 1000)
    val giftMessage: String? = null,
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
    @field:Valid val deliverySlotSelections: List<DeliverySlotSelectionRequest> = emptyList(),
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

/**
 * 주문 항목(스냅샷) 응답. [unitPrice] 는 항상 정가이며, 타임딜이 적용된 항목은 [flashSaleId]/
 * [appliedSalePrice] 가 채워진다(정가 취소선 + 특가 표시용, `docs/planning/flash-sale.md` AC4).
 */
data class OrderItemResponse(
    val optionId: Long,
    val productName: String,
    val optionName: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
    val flashSaleId: Long?,
    val appliedSalePrice: Long?,
) {
    companion object {
        fun from(item: OrderItem) = OrderItemResponse(
            optionId = item.optionId,
            productName = item.productName,
            optionName = item.optionName,
            unitPrice = item.unitPrice,
            quantity = item.quantity,
            lineTotal = item.lineTotal,
            flashSaleId = item.flashSaleId,
            appliedSalePrice = item.appliedSalePrice,
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
    val deliverySlotId: Long?,
    val deliveryFee: Long,
    val items: List<OrderItemResponse>,
    // 발송 후에만 채워진다(배송 조회는 GET /api/orders/sub-orders/{id}/tracking)
    val courier: String? = null,
    val trackingNumber: String? = null,
) {
    companion object {
        fun from(subOrder: SubOrder) = SubOrderResponse(
            subOrderId = requireNotNull(subOrder.id),
            sellerId = requireNotNull(subOrder.seller.id),
            storeName = subOrder.seller.storeName,
            status = subOrder.status,
            subtotal = subOrder.subtotal,
            deliverySlotId = subOrder.deliverySlotId,
            deliveryFee = subOrder.deliveryFee,
            items = subOrder.items.map { OrderItemResponse.from(it) },
            courier = subOrder.shipment?.courier,
            trackingNumber = subOrder.shipment?.trackingNumber,
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

/**
 * 주문 상세 응답.
 *
 * [shippingAddress] 는 선물 주문([isGift])이면 항상 null 이다 — 수령자가 입력한 배송지는 구매자에게
 * 노출하지 않는다는 개인정보 보호 정책(`docs/planning/gift-order.md` §4/§9 오픈이슈 #3)에 따라
 * 수락 여부와 무관하게 마스킹한다. 배송 진행 상황은 하위 주문 [subOrders] 의 상태로만 안내한다.
 *
 * [giftClaimToken] 은 주문 생성 응답에서만 채워진다(공유 링크 안내용). 이후 재조회 시 링크 상태가
 * 필요하면 `GET /api/orders/{orderId}/gift` ([com.example.starter.domain.gift.GiftOrderService.getStatus])
 * 를 사용한다.
 */
data class OrderResponse(
    val orderId: Long,
    val orderNumber: String,
    val status: OrderStatus,
    val totalAmount: Long,
    val discountAmount: Long,
    val pointUsed: Long,
    val deliveryFeeTotal: Long,
    val payableAmount: Long,
    val ordererName: String,
    val shippingAddress: ShippingAddressResponse?,
    val createdAt: Instant,
    val subOrders: List<SubOrderResponse>,
    val isGift: Boolean = false,
    val giftMessage: String? = null,
    val giftClaimToken: String? = null,
) {
    companion object {
        fun from(order: Order, giftClaimToken: String? = null) = OrderResponse(
            orderId = requireNotNull(order.id),
            orderNumber = order.orderNumber,
            status = order.status,
            totalAmount = order.totalAmount,
            discountAmount = order.discountAmount,
            pointUsed = order.pointUsed,
            deliveryFeeTotal = order.deliveryFeeTotal,
            payableAmount = order.payableAmount,
            ordererName = order.ordererName,
            // 선물 주문은 수령자 프라이버시 보호를 위해 배송지를 항상 마스킹한다(위 클래스 설명 참고).
            shippingAddress = if (order.isGift) null else order.shippingAddress?.let { ShippingAddressResponse.from(it) },
            createdAt = order.createdAt,
            subOrders = order.subOrders
                .sortedBy { it.id }
                .map { SubOrderResponse.from(it) },
            isGift = order.isGift,
            giftMessage = order.giftMessage,
            giftClaimToken = giftClaimToken,
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
