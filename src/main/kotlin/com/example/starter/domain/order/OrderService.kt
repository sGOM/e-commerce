package com.example.starter.domain.order

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.cart.entity.Cart
import com.example.starter.domain.cart.repository.CartRepository
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.repository.InventoryRepository
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.coupon.CouponService
import com.example.starter.domain.point.PointService
import com.example.starter.domain.order.dto.CreateOrderRequest
import com.example.starter.domain.order.dto.GuestOrderItemRequest
import com.example.starter.domain.order.dto.GuestOrderLookupRequest
import com.example.starter.domain.order.dto.GuestOrderRequest
import com.example.starter.domain.order.dto.OrderResponse
import com.example.starter.domain.order.dto.OrderSummaryResponse
import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.OrderItem
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.ShippingAddress
import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.seller.entity.Seller
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 회원 주문. 장바구니 전체를 주문으로 전환하며, 한 주문에 여러 판매자 상품이 섞이면
 * [SubOrder] 로 판매자 단위 분리한다. 재고는 원자적 UPDATE 로 예약해 동시 주문 시 초과 판매를 막는다.
 *
 * 가격/상품명/옵션은 주문 시점 스냅샷으로 보관한다(원본 변경과 무관). 결제·쿠폰·포인트는 Phase 4.
 */
@Service
@Transactional(readOnly = true)
class OrderService(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val subOrderRepository: SubOrderRepository,
    private val inventoryRepository: InventoryRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val paymentRepository: PaymentRepository,
    private val couponService: CouponService,
    private val pointService: PointService,
) {

    /** 주문 조립에 필요한 항목 스냅샷(옵션 LAZY 접근을 한곳에서 끝낸다). */
    private data class OrderLine(
        val seller: Seller,
        val optionId: Long,
        val productName: String,
        val optionName: String,
        val unitPrice: Long,
        val quantity: Int,
        val purchasable: Boolean,
    )

    @Transactional
    fun createFromCart(userId: Long, request: CreateOrderRequest): OrderResponse {
        val cart = cartRepository.findWithItemsByUserId(userId)
            .filter { it.items.isNotEmpty() }
            .orElseThrow { BusinessException(ErrorCode.EMPTY_ORDER) }

        val lines = cart.items.map { toLine(it.option, it.quantity) }
        val order = buildOrder(
            userId = userId,
            ordererName = request.ordererName!!,
            ordererPhone = request.ordererPhone!!,
            ordererEmail = request.ordererEmail!!,
            address = request.shippingAddress!!.toEmbeddable(),
            lines = lines,
        )

        // 쿠폰 적용(상품합계 기준 할인) → 포인트 사용(남은 결제금액 한도) — 회원 전용
        val orderId = requireNotNull(order.id)
        request.issuedCouponId?.let { couponId ->
            order.discountAmount = couponService.applyToOrder(userId, couponId, orderId, order.totalAmount)
        }
        if (request.usePoint > 0) {
            val maxUsable = order.totalAmount - order.discountAmount
            pointService.use(userId, request.usePoint, orderId, maxUsable)
            order.pointUsed = request.usePoint
        }
        order.recalculateAmounts() // 할인/포인트 반영해 최종 결제금액 재계산
        order.distributePayable() // 부분 환불 단위로 하위 주문에 결제금액 배분

        cart.clear() // 주문 전환된 장바구니 비우기
        return OrderResponse.from(order)
    }

    /** 게스트(비회원) 주문. 장바구니 없이 전달된 항목으로 주문을 만든다. 쿠폰/포인트는 미지원. */
    @Transactional
    fun createGuestOrder(request: GuestOrderRequest): OrderResponse {
        val lines = mergeGuestItems(request.items).map { (optionId, quantity) ->
            val option = productOptionRepository.findWithProductAndInventoryById(optionId)
                .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }
            toLine(option, quantity)
        }
        val order = buildOrder(
            userId = null,
            ordererName = request.ordererName!!,
            ordererPhone = request.ordererPhone!!,
            ordererEmail = request.ordererEmail!!,
            address = request.shippingAddress!!.toEmbeddable(),
            lines = lines,
        )
        order.distributePayable() // 게스트는 쿠폰/포인트가 없어 payableShare = subtotal
        return OrderResponse.from(order)
    }

    /** 게스트 주문 조회 — 주문번호 + 주문 시 연락처 일치 검증. */
    fun lookupGuestOrder(request: GuestOrderLookupRequest): OrderResponse {
        val order = orderRepository.findByOrderNumber(request.orderNumber!!)
            .filter { it.ordererPhone == request.ordererPhone }
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        return OrderResponse.from(order)
    }

    /**
     * 공통 주문 조립: 판매상태 검증 → 재고 원자 예약 → 판매자 단위 SubOrder 분리 → 저장.
     * 쿠폰/포인트 적용 전 상태(상품합계만 반영)의 [Order] 를 영속화해 반환한다.
     */
    private fun buildOrder(
        userId: Long?,
        ordererName: String,
        ordererPhone: String,
        ordererEmail: String,
        address: ShippingAddress,
        lines: List<OrderLine>,
    ): Order {
        // 1) 판매 상태 검증
        lines.forEach { line ->
            if (!line.purchasable) {
                throw BusinessException(
                    ErrorCode.PRODUCT_NOT_PURCHASABLE,
                    "'${line.productName}' 은(는) 현재 구매할 수 없습니다.",
                )
            }
        }
        // 2) 재고 예약 — 원자적 UPDATE. 영향 행이 0이면 재고 부족(예외 → 전체 롤백, 앞선 예약도 복원)
        lines.forEach { line ->
            if (inventoryRepository.reserve(line.optionId, line.quantity) == 0) {
                throw BusinessException(
                    ErrorCode.INSUFFICIENT_STOCK,
                    "'${line.productName}' 의 재고가 부족합니다.",
                )
            }
        }
        // 3) 판매자 단위로 SubOrder 분리하여 주문 조립
        val order = Order(
            orderNumber = generateOrderNumber(),
            userId = userId,
            ordererName = ordererName,
            ordererPhone = ordererPhone,
            ordererEmail = ordererEmail,
            shippingAddress = address,
        )
        lines.groupBy { it.seller.id }.values.forEach { sellerLines ->
            val subOrder = SubOrder(seller = sellerLines.first().seller)
            sellerLines.forEach { line ->
                subOrder.addItem(
                    OrderItem(
                        optionId = line.optionId,
                        productName = line.productName,
                        optionName = line.optionName,
                        unitPrice = line.unitPrice,
                        quantity = line.quantity,
                    ),
                )
            }
            subOrder.recalculateSubtotal()
            order.addSubOrder(subOrder)
        }
        order.recalculateAmounts() // 상품합계 확정(쿠폰/포인트 적용 전)
        orderRepository.save(order) // 쿠폰/포인트 연결·응답에 필요한 order.id 확보
        return order
    }

    /** 게스트 요청 항목 중 같은 옵션은 수량을 합산한다(중복 줄 방지). */
    private fun mergeGuestItems(items: List<GuestOrderItemRequest>): Map<Long, Int> {
        val merged = LinkedHashMap<Long, Int>()
        items.forEach { merged[it.optionId!!] = (merged[it.optionId] ?: 0) + it.quantity!! }
        return merged
    }

    fun getMyOrders(userId: Long, pageable: Pageable): PageResponse<OrderSummaryResponse> =
        PageResponse.of(orderRepository.findByUserId(userId, pageable)) { OrderSummaryResponse.from(it) }

    fun getOrderDetail(userId: Long, orderId: Long): OrderResponse =
        OrderResponse.from(findOwnedOrder(userId, orderId))

    /**
     * 주문 취소. 배송 시작 전이면 결제 전(CREATED)·결제 후(PAID) 모두 취소 가능하다.
     * 공통으로 재고·쿠폰·사용포인트를 복원하고, 결제 후 취소는 결제를 환불 처리하고 적립 포인트를 회수한다.
     */
    @Transactional
    fun cancelOrder(userId: Long, orderId: Long): OrderResponse {
        val order = findOwnedOrder(userId, orderId)
        if (order.status == OrderStatus.CANCELED || order.hasShipmentStarted) {
            throw BusinessException(ErrorCode.ORDER_NOT_CANCELABLE)
        }
        doCancel(order, "주문 취소")
        return OrderResponse.from(order)
    }

    /**
     * 하위 주문(판매자 단위) 부분 취소. 해당 SubOrder 의 재고만 복원하고 결제 후라면 payableShare 만큼 부분 환불한다.
     * 모든 SubOrder 가 취소되면 그 시점에 쿠폰/포인트 복원·적립 회수·결제 취소를 마무리한다.
     */
    @Transactional
    fun cancelSubOrder(userId: Long, subOrderId: Long): OrderResponse {
        val subOrder = subOrderRepository.findById(subOrderId)
            .orElseThrow { BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) }
        val order = subOrder.order
        if (order.userId != userId) {
            throw BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) // 본인 주문이 아니면 존재를 숨긴다
        }
        if (subOrder.status == SubOrderStatus.CANCELED ||
            subOrder.status == SubOrderStatus.SHIPPED ||
            subOrder.status == SubOrderStatus.DELIVERED
        ) {
            throw BusinessException(ErrorCode.ORDER_NOT_CANCELABLE)
        }

        val orderId = requireNotNull(order.id)
        val wasPaid = order.status == OrderStatus.PAID

        subOrder.items.forEach { item -> inventoryRepository.release(item.optionId, item.quantity) }
        subOrder.status = SubOrderStatus.CANCELED
        if (wasPaid) {
            paymentRepository.findByOrderId(orderId).ifPresent { it.recordPartialRefund(subOrder.payableShare) }
        }

        // 전체 취소 완료 시점에만 쿠폰/포인트 복원·적립 회수·결제 취소를 마무리
        if (order.isFullyCanceled) {
            if (order.discountAmount > 0) {
                couponService.restoreForOrder(orderId)
            }
            if (order.pointUsed > 0) {
                order.userId?.let { pointService.restoreUse(it, order.pointUsed, orderId) }
            }
            if (wasPaid) {
                paymentRepository.findByOrderId(orderId).ifPresent { it.markCanceled("전체 취소 완료") }
                order.userId?.let { pointService.revokeEarnForOrder(it, orderId) }
            }
            order.status = OrderStatus.CANCELED
        }
        return OrderResponse.from(order)
    }

    /** 관리자 환불. 본인 확인 없이 환불하며, 배송 이후(반품)도 허용한다. 이미 취소된 주문만 거부. */
    @Transactional
    fun refundByAdmin(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.ORDER_NOT_CANCELABLE)
        }
        doCancel(order, "관리자 환불")
        return OrderResponse.from(order)
    }

    /** 취소/환불 공통 처리: 재고·쿠폰·사용포인트 복원, 결제 후라면 결제 환불 + 적립 포인트 회수. */
    private fun doCancel(order: Order, reason: String) {
        val orderId = requireNotNull(order.id)
        order.subOrders.forEach { subOrder ->
            subOrder.items.forEach { item -> inventoryRepository.release(item.optionId, item.quantity) }
            subOrder.status = SubOrderStatus.CANCELED
        }
        if (order.discountAmount > 0) {
            couponService.restoreForOrder(orderId)
        }
        if (order.pointUsed > 0) {
            order.userId?.let { pointService.restoreUse(it, order.pointUsed, orderId) }
        }
        if (order.status == OrderStatus.PAID) {
            paymentRepository.findByOrderId(orderId).ifPresent { it.markCanceled(reason) }
            order.userId?.let { pointService.revokeEarnForOrder(it, orderId) }
        }
        order.status = OrderStatus.CANCELED
    }

    private fun findOwnedOrder(userId: Long, orderId: Long): Order =
        orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }

    private fun toLine(option: ProductOption, quantity: Int): OrderLine {
        val product = option.product
        return OrderLine(
            seller = product.seller,
            optionId = requireNotNull(option.id),
            productName = product.name,
            optionName = option.name,
            unitPrice = product.basePrice + option.additionalPrice,
            quantity = quantity,
            purchasable = product.status.isPurchasable,
        )
    }

    private fun generateOrderNumber(): String {
        val date = LocalDate.now(KST).format(DATE_FORMAT)
        val suffix = UUID.randomUUID().toString().substring(0, 8).uppercase()
        return "ORD-$date-$suffix"
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }
}
