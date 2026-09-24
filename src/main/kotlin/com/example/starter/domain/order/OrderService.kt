package com.example.starter.domain.order

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.cart.entity.Cart
import com.example.starter.domain.cart.repository.CartRepository
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.event.InventoryReservedEvent
import com.example.starter.domain.catalog.repository.InventoryRepository
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.coupon.CouponService
import com.example.starter.domain.delivery.entity.DeliverySlotType
import com.example.starter.domain.delivery.repository.DeliveryRegionRepository
import com.example.starter.domain.delivery.repository.DeliverySlotRepository
import com.example.starter.domain.flashsale.repository.FlashSaleRepository
import com.example.starter.domain.gift.GiftClaimService
import com.example.starter.domain.membership.MembershipBenefitService
import com.example.starter.domain.point.PointService
import com.example.starter.domain.order.dto.ClaimGuestOrderRequest
import com.example.starter.domain.order.dto.CreateOrderRequest
import com.example.starter.domain.order.dto.DeliverySlotSelectionRequest
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
import com.example.starter.domain.payment.PaymentService
import com.example.starter.domain.seller.entity.Seller
import org.springframework.data.domain.Pageable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
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
    private val paymentService: PaymentService,
    private val couponService: CouponService,
    private val pointService: PointService,
    private val flashSaleRepository: FlashSaleRepository,
    private val deliverySlotRepository: DeliverySlotRepository,
    private val deliveryRegionRepository: DeliveryRegionRepository,
    private val membershipBenefitService: MembershipBenefitService,
    private val giftClaimService: GiftClaimService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    /** 주문 조립에 필요한 항목 스냅샷(옵션 LAZY 접근을 한곳에서 끝낸다). */
    private data class OrderLine(
        val seller: Seller,
        val optionId: Long,
        val productName: String,
        val optionName: String,
        val unitPrice: Long, // 정가 스냅샷(기본가+옵션추가금) — 타임딜 적용 여부와 무관하게 항상 정가
        val quantity: Int,
        val purchasable: Boolean,
        // 주문 시점에 이 옵션에 진행 중인 타임딜이 있으면 채워진다(서버가 판단, 클라이언트 입력 아님).
        val flashSaleId: Long? = null,
        val appliedSalePrice: Long? = null,
        // 새벽배송 가능 상품인지(Product.dawnDeliveryEligible) — 배송 슬롯 선택 가능 여부 판단에 쓰인다.
        val dawnDeliveryEligible: Boolean = false,
    )

    /**
     * 회원 주문 생성. 선물 주문([CreateOrderRequest.isGift] = true, `docs/planning/gift-order.md`)은
     * 배송지를 생략할 수 있는 대신, 배송 슬롯처럼 배송지에 의존하는 옵션은 선택할 수 없다(§9 오픈이슈
     * #1). 일반 주문은 기존과 동일하게 배송지가 필수다.
     */
    @Transactional
    fun createFromCart(userId: Long, request: CreateOrderRequest): OrderResponse {
        val cart = cartRepository.findWithItemsByUserId(userId)
            .filter { it.items.isNotEmpty() }
            .orElseThrow { BusinessException(ErrorCode.EMPTY_ORDER) }

        val address = if (request.isGift) {
            if (request.deliverySlotSelections.isNotEmpty()) {
                throw BusinessException(ErrorCode.GIFT_DELIVERY_SLOT_NOT_SUPPORTED)
            }
            request.shippingAddress?.toEmbeddable() // 선물은 배송지 없이 결제까지 진행 가능(AC1/AC2)
        } else {
            request.shippingAddress?.toEmbeddable() ?: throw BusinessException(ErrorCode.ORDER_SHIPPING_ADDRESS_REQUIRED)
        }

        val lines = cart.items.map { toLine(it.option, it.quantity) }
        val order = buildOrder(
            userId = userId,
            ordererName = request.ordererName!!,
            ordererPhone = request.ordererPhone!!,
            ordererEmail = request.ordererEmail!!,
            address = address,
            lines = lines,
            slotSelections = toSlotSelectionMap(request.deliverySlotSelections),
            isGift = request.isGift,
            giftMessage = request.giftMessage,
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

        // 선물 주문이면 공유 링크(토큰)를 즉시 발급한다(AC4) — 응답에 담아 주문 완료 화면에서 안내한다.
        val giftClaimToken = if (order.isGift) giftClaimService.createForOrder(orderId).token else null
        return OrderResponse.from(order, giftClaimToken)
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
            slotSelections = toSlotSelectionMap(request.deliverySlotSelections),
        )
        order.distributePayable() // 게스트는 쿠폰/포인트가 없어 payableShare = subtotal
        return OrderResponse.from(order)
    }

    /**
     * 정기배송 배치 전용 주문 생성(`docs/planning/subscription-delivery.md` AC6). 장바구니 없이
     * 단일 옵션+수량으로 즉시 주문을 조립한다. 쿠폰/포인트는 적용하지 않는다(자동 생성 주문은 사람이
     * 선택하는 액션인 쿠폰/포인트 적용 대상이 아니라는 기획서 §4 원칙). 가격은 항상 호출 시점(=회차
     * 처리 시점) 현재가를 스냅샷한다(AC9) — 구독 등록 시점 가격 고정 없음.
     *
     * 결제는 이 메서드가 하지 않고 `CREATED`(재고 예약 완료, 결제 대기) 상태로 반환한다. 호출측
     * ([com.example.starter.domain.subscription] 도메인)이 빌링키로 결제를 시도해 성공하면 PAID로
     * 전이시키고, 실패하면 [cancelOrder] 로 재고 예약을 되돌린다.
     */
    @Transactional
    fun createSubscriptionOrder(
        userId: Long,
        optionId: Long,
        quantity: Int,
        ordererName: String,
        ordererPhone: String,
        ordererEmail: String,
        shippingAddress: ShippingAddress,
    ): Order {
        val option = productOptionRepository.findWithProductAndInventoryById(optionId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }
        val line = toLine(option, quantity)
        val order = buildOrder(userId, ordererName, ordererPhone, ordererEmail, shippingAddress, listOf(line))
        order.distributePayable() // 구독 주문은 쿠폰/포인트가 없어 payableShare = subtotal(+배송비)
        return order
    }

    /** 게스트 주문 조회 — 주문번호 + 주문 시 연락처 일치 검증. */
    fun lookupGuestOrder(request: GuestOrderLookupRequest): OrderResponse {
        val order = orderRepository.findByOrderNumberAndOrdererPhone(request.orderNumber!!, request.ordererPhone!!)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        return OrderResponse.from(order)
    }

    /**
     * 게스트 주문을 로그인한 회원 계정에 연결(claim)한다. 주문번호 + 주문 시 연락처로 본인 확인 후
     * 해당 게스트 주문(userId == null)의 소유자를 회원으로 채운다. 이미 회원 주문이면 거부한다.
     */
    @Transactional
    fun claimGuestOrder(userId: Long, request: ClaimGuestOrderRequest): OrderResponse {
        val order = orderRepository.findByOrderNumberAndOrdererPhone(request.orderNumber!!, request.ordererPhone!!)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.userId != null) {
            throw BusinessException(ErrorCode.ORDER_ALREADY_CLAIMED)
        }
        order.claimBy(userId)
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
        address: ShippingAddress?,
        lines: List<OrderLine>,
        slotSelections: Map<Long, Long> = emptyMap(),
        isGift: Boolean = false,
        giftMessage: String? = null,
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
        //    타임딜이 적용된 라인은 한도 수량도 함께(둘 다 통과해야 성공) 원자적 UPDATE 로 예약한다(AC6).
        //    재고는 충분하지만 딜 한도가 그 사이 소진된 경우(동시 주문 경쟁)에도 여기서 걸러진다 —
        //    이 경우 전체 롤백 후 재시도하면 타임딜은 이미 종료된 것으로 보여 정가로 재주문된다.
        val now = Instant.now()
        lines.forEach { line ->
            if (inventoryRepository.reserve(line.optionId, line.quantity) == 0) {
                throw BusinessException(
                    ErrorCode.INSUFFICIENT_STOCK,
                    "'${line.productName}' 의 재고가 부족합니다.",
                )
            }
            eventPublisher.publishEvent(InventoryReservedEvent(line.optionId, line.quantity))
            if (line.flashSaleId != null && flashSaleRepository.reserve(line.flashSaleId, line.quantity, now) == 0) {
                throw BusinessException(
                    ErrorCode.FLASH_SALE_SOLD_OUT,
                    "'${line.productName}' 타임딜 한정수량이 모두 소진되었습니다.",
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
            isGift = isGift,
            giftMessage = giftMessage,
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
                        flashSaleId = line.flashSaleId,
                        appliedSalePrice = line.appliedSalePrice,
                    ),
                )
            }
            subOrder.recalculateSubtotal()
            val sellerId = requireNotNull(sellerLines.first().seller.id)
            // 배송 슬롯은 배송지 기준 검증이 필요해 주소가 없으면(선물 미확정) 적용 대상이 아니다 —
            // 실제로는 선물 주문이 slotSelections 를 비워 보내도록 상위에서 막아 이 분기는 도달하지 않는다.
            slotSelections[sellerId]?.let { slotId ->
                val nonNullAddress = address ?: throw BusinessException(ErrorCode.GIFT_DELIVERY_SLOT_NOT_SUPPORTED)
                applyDeliverySlot(subOrder, slotId, sellerLines, nonNullAddress, now, userId)
            }
            order.addSubOrder(subOrder)
        }
        order.recalculateAmounts() // 상품합계 확정(쿠폰/포인트 적용 전)
        orderRepository.save(order) // 쿠폰/포인트 연결·응답에 필요한 order.id 확보
        return order
    }

    /**
     * 배송 슬롯 선택(AC6/AC7) — 새벽배송 가능 상품이 포함된 SubOrder 에 한해 허용하고(AC3), 새벽배송
     * 슬롯은 배송지가 화이트리스트 지역일 때만(AC5), 권역 한정 슬롯은 배송지가 그 권역일 때만 허용한다.
     * 정원 예약은 재고와 동일한 단일 원자적 UPDATE 로 하며, 영향 행이 0이면 마감/정원초과로 판단한다(AC7).
     *
     * 멤버십 무료배송 혜택(`docs/planning/subscription-membership.md` AC8)이 활성인 회원은 슬롯 추가
     * 배송비를 0원으로 스냅샷한다 — 정원은 그대로 소모하되(자리는 실제로 쓰므로) 결제 금액에는 반영하지
     * 않는다. 게스트([userId] == null)는 멤버십 대상이 아니므로 항상 정가 배송비가 적용된다.
     */
    private fun applyDeliverySlot(
        subOrder: SubOrder,
        slotId: Long,
        sellerLines: List<OrderLine>,
        address: ShippingAddress,
        now: Instant,
        userId: Long?,
    ) {
        if (sellerLines.none { it.dawnDeliveryEligible }) {
            throw BusinessException(ErrorCode.DELIVERY_SLOT_NOT_APPLICABLE, "새벽배송 대상 상품이 없어 배송 슬롯을 선택할 수 없습니다.")
        }
        val slot = deliverySlotRepository.findById(slotId)
            .orElseThrow { BusinessException(ErrorCode.DELIVERY_SLOT_NOT_FOUND) }
        if (slot.type == DeliverySlotType.DAWN && !deliveryRegionRepository.existsDawnAvailable(address.zipcode)) {
            throw BusinessException(ErrorCode.DELIVERY_SLOT_NOT_APPLICABLE, "배송지가 새벽배송 가능 지역이 아닙니다.")
        }
        val regionScope = slot.regionScope
        if (regionScope != null && !address.zipcode.startsWith(regionScope)) {
            throw BusinessException(ErrorCode.DELIVERY_SLOT_NOT_APPLICABLE, "해당 슬롯은 이 배송지 권역에 적용되지 않습니다.")
        }
        if (deliverySlotRepository.reserve(slotId, now) == 0) {
            throw BusinessException(ErrorCode.DELIVERY_SLOT_SOLD_OUT, "선택한 배송 슬롯이 마감되었거나 정원이 초과되었습니다.")
        }
        subOrder.deliverySlotId = slotId
        subOrder.deliveryFee = if (userId != null && membershipBenefitService.isFreeShippingActive(userId, now)) {
            0
        } else {
            slot.extraFee
        }
    }

    /** 요청의 슬롯 선택 목록을 sellerId 기준 맵으로 변환한다(같은 판매자 중복 선택은 뒤 항목이 덮어씀). */
    private fun toSlotSelectionMap(selections: List<DeliverySlotSelectionRequest>): Map<Long, Long> =
        selections.associate { requireNotNull(it.sellerId) to requireNotNull(it.deliverySlotId) }

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

        subOrder.items.forEach { item ->
            inventoryRepository.release(item.optionId, item.quantity)
            // 딜이 이미 종료/강제종료 됐어도 한도는 복원한다(AC7, 통계 정확성 목적).
            item.flashSaleId?.let { flashSaleRepository.release(it, item.quantity) }
        }
        // 슬롯이 마감/소진된 뒤에도 정원은 복원한다(AC9, FlashSale.release 와 동일 원칙).
        subOrder.deliverySlotId?.let { deliverySlotRepository.release(it) }
        subOrder.status = SubOrderStatus.CANCELED

        // 전체 취소 완료 시점에만 쿠폰/포인트 복원·적립 회수·결제 취소를 마무리
        if (order.isFullyCanceled) {
            if (order.discountAmount > 0) {
                couponService.restoreForOrder(orderId)
            }
            if (order.pointUsed > 0) {
                order.userId?.let { pointService.restoreUse(it, order.pointUsed, orderId) }
            }
            if (wasPaid) {
                order.userId?.let { pointService.revokeEarnForOrder(it, orderId) }
            }
            order.status = OrderStatus.CANCELED
        }
        // PG 취소는 내부 상태 변경을 모두 끝낸 뒤 마지막에(실패 시 전체 롤백) — PaymentService.refund 참고
        if (wasPaid) {
            val fully = order.isFullyCanceled
            paymentService.refund(
                orderId, subOrder.payableShare, if (fully) "전체 취소 완료" else "부분 취소", "cancel-sub-$subOrderId", fully,
            )
        }
        return OrderResponse.from(order)
    }

    /**
     * 구매자 수령 확인(구매확정). SHIPPED → DELIVERED 전이하며, 이 시점부터 해당 하위 주문의
     * 항목들이 리뷰 작성 가능 대상이 된다([review] 도메인). 본인 주문이 아니면 존재를 숨긴다.
     */
    @Transactional
    fun confirmDelivery(userId: Long, subOrderId: Long): OrderResponse {
        val subOrder = subOrderRepository.findById(subOrderId)
            .orElseThrow { BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) }
        if (subOrder.order.userId != userId) {
            throw BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) // 본인 주문이 아니면 존재를 숨긴다
        }
        if (subOrder.status != SubOrderStatus.SHIPPED) {
            throw BusinessException(ErrorCode.SUB_ORDER_NOT_DELIVERABLE)
        }
        subOrder.confirmDelivery()
        return OrderResponse.from(subOrder.order)
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

    /**
     * 선물 미수락 만료 자동 취소(배치 전용, AC9,
     * [com.example.starter.domain.gift.GiftExpiryBatchService]). [refundByAdmin] 과 동일하게 소유자
     * 확인 없이 전액 환불하되, 결제 이력에 남는 사유를 다르게 구분한다. 미수락 상태(배송지 미확정)라
     * 배송이 시작될 수 없으므로 [Order.hasShipmentStarted] 검사는 불필요하다.
     */
    @Transactional
    fun cancelExpiredGiftOrder(orderId: Long): OrderResponse {
        val order = orderRepository.findById(orderId)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.ORDER_NOT_CANCELABLE)
        }
        doCancel(order, "선물 미수락 만료 자동 취소")
        return OrderResponse.from(order)
    }

    /**
     * 결제 기한이 지난 미결제 주문 자동 취소(배치 전용, [UnpaidOrderExpiryService]). 건별 트랜잭션(REQUIRES_NEW)으로
     * 한 건의 실패가 다른 건을 되돌리지 않게 하고, 주문 행을 잠가 진행 중인 결제([PaymentService.pay] 도 같은 행을
     * 잠근다)와 겹치지 않게 한다. 잠금 뒤 상태를 다시 보므로 그 사이 결제된 주문은 건드리지 않는다.
     * 결제 전이라 PG 호출은 없고, 선물 주문이면 수령 링크도 함께 마감한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun expireUnpaidOrder(orderId: Long, createdBefore: Instant): Boolean {
        val order = orderRepository.findWithLockById(orderId).orElse(null) ?: return false
        if (order.status != OrderStatus.CREATED || !order.createdAt.isBefore(createdBefore)) return false
        doCancel(order, "미결제 자동 취소")
        if (order.isGift) giftClaimService.cancelForOrder(orderId)
        return true
    }

    /**
     * PG 에서 이미 취소된 결제를 주문에 반영(결제 웹훅, [com.example.starter.domain.payment.PaymentWebhookService]).
     * PG 가 이미 환불했으므로 배송 여부와 무관하게 내부 상태만 맞추고 PG 는 다시 호출하지 않는다. 이미 취소면 무시(웹훅 재전송 멱등).
     */
    @Transactional
    fun cancelByPg(orderNumber: String) {
        val order = orderRepository.findByOrderNumber(orderNumber).orElse(null) ?: return
        if (order.status != OrderStatus.PAID) return
        doCancel(order, "PG 취소 반영", refundAtPg = false)
    }

    /** 취소/환불 공통 처리: 재고·쿠폰·사용포인트 복원, 결제 후라면 결제 환불 + 적립 포인트 회수. */
    private fun doCancel(order: Order, reason: String, refundAtPg: Boolean = true) {
        val orderId = requireNotNull(order.id)
        val wasPaid = order.status == OrderStatus.PAID
        // 이미 부분 취소로 환불된 SubOrder 몫은 빼고 남은 금액만 PG 에 취소 요청한다.
        val remainingPayable = order.subOrders.filter { it.status != SubOrderStatus.CANCELED }.sumOf { it.payableShare }
        order.subOrders.forEach { subOrder ->
            subOrder.items.forEach { item ->
                inventoryRepository.release(item.optionId, item.quantity)
                item.flashSaleId?.let { flashSaleRepository.release(it, item.quantity) }
            }
            // 이미 부분취소(cancelSubOrder)로 슬롯을 복원한 SubOrder 는 다시 복원하지 않는다(이중 복원 방지).
            if (subOrder.status != SubOrderStatus.CANCELED) {
                subOrder.deliverySlotId?.let { deliverySlotRepository.release(it) }
            }
            subOrder.status = SubOrderStatus.CANCELED
        }
        if (order.discountAmount > 0) {
            couponService.restoreForOrder(orderId)
        }
        if (order.pointUsed > 0) {
            order.userId?.let { pointService.restoreUse(it, order.pointUsed, orderId) }
        }
        if (wasPaid) {
            order.userId?.let { pointService.revokeEarnForOrder(it, orderId) }
        }
        order.status = OrderStatus.CANCELED
        // PG 취소는 마지막에(실패 시 전체 롤백) — PaymentService.refund 참고
        if (wasPaid) {
            paymentService.refund(orderId, remainingPayable, reason, "cancel-order-$orderId", fullyCanceled = true, callPg = refundAtPg)
        }
    }

    private fun findOwnedOrder(userId: Long, orderId: Long): Order =
        orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }

    /**
     * 옵션에 지금 이 순간 진행 중인 타임딜이 있으면 특가를 조회해 라인에 스냅샷한다(AC4). 서버가
     * 직접 판단하며 클라이언트가 보낸 가격/딜 식별자는 신뢰하지 않는다(서버 금액 계산 원칙).
     */
    private fun toLine(option: ProductOption, quantity: Int): OrderLine {
        val product = option.product
        val flashSale = flashSaleRepository.findOngoingByOptionId(requireNotNull(option.id), Instant.now()).orElse(null)
        return OrderLine(
            seller = product.seller,
            optionId = requireNotNull(option.id),
            productName = product.name,
            optionName = option.name,
            unitPrice = product.basePrice + option.additionalPrice,
            quantity = quantity,
            purchasable = product.status.isPurchasable,
            flashSaleId = flashSale?.id,
            appliedSalePrice = flashSale?.salePrice,
            dawnDeliveryEligible = product.dawnDeliveryEligible,
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
