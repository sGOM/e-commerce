package com.example.starter.domain.order.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

/**
 * 주문 헤더. 여러 판매자의 상품을 한 번에 결제하면 [SubOrder] 로 판매자 단위 분리된다.
 *
 * 금액은 전부 서버 계산이며 클라이언트 값을 신뢰하지 않는다. 게스트 주문도 표현하기 위해
 * 주문자 연락처([ordererName]/[ordererPhone]/[ordererEmail])를 보관한다([userId] 는 게스트면 null).
 *
 * 선물 주문([isGift] = true, `docs/planning/gift-order.md`)은 [shippingAddress] 없이 생성되고
 * 결제까지 진행된다 — 수령자가 [com.example.starter.domain.gift.GiftClaimService] 를 통해 배송지를
 * 입력(수락)하면 [assignGiftShippingAddress] 로 뒤늦게 채워진다. 그 전에는 배송(SHIPPED)으로 전이할
 * 수 없다([SubOrder.isShippable] 가드).
 */
@Entity
@Table(name = "orders")
class Order(
    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    val orderNumber: String,

    @Column(name = "user_id")
    var userId: Long?, // 게스트 주문은 null. 게스트→회원 연결(claim) 시 회원 id 로 채워진다.

    @Column(name = "orderer_name", nullable = false, length = 100)
    val ordererName: String,

    @Column(name = "orderer_phone", nullable = false, length = 30)
    val ordererPhone: String,

    @Column(name = "orderer_email", nullable = false, length = 255)
    val ordererEmail: String,

    // 선물 주문은 생성 시점에 null 로 시작해 수령자 수락 시 채워진다(그 외에는 항상 생성 시점에 채워짐).
    @Embedded
    var shippingAddress: ShippingAddress?,

    @Column(name = "is_gift", nullable = false)
    val isGift: Boolean = false,

    @Column(name = "gift_message", columnDefinition = "text")
    val giftMessage: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.CREATED

    @Column(name = "total_amount", nullable = false)
    var totalAmount: Long = 0

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Long = 0 // 쿠폰 (Phase 4)

    @Column(name = "point_used", nullable = false)
    var pointUsed: Long = 0 // 포인트 (Phase 4)

    @Column(name = "payable_amount", nullable = false)
    var payableAmount: Long = 0

    // 배송 슬롯 추가요금 합계(SubOrder.deliveryFee 의 합, `docs/planning/delivery-slot.md`).
    // 공통 배송비 모델이 아직 없어(오픈 이슈) 슬롯 이용 시에 한해 결제금액에 반영한다.
    @Column(name = "delivery_fee_total", nullable = false)
    var deliveryFeeTotal: Long = 0

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    val subOrders: MutableList<SubOrder> = mutableListOf()

    /** 게스트 주문을 회원 계정에 연결한다. 이미 회원 주문이면 거부. */
    fun claimBy(memberId: Long) {
        if (userId != null) throw IllegalStateException("이미 회원에 연결된 주문")
        userId = memberId
    }

    /**
     * 선물 수령자가 배송지를 입력(수락)하면 호출된다([com.example.starter.domain.gift.GiftClaimService]).
     * 선물 주문에서만 허용하며(일반 주문은 생성 시점에 이미 확정), 이미 배송지가 있으면(중복 수락 등)
     * 거부한다 — 정상 흐름이라면 [com.example.starter.domain.gift.entity.GiftClaim] 상태 가드가
     * 먼저 막지만, 엔티티 레벨에서도 이중으로 방어한다.
     */
    fun assignGiftShippingAddress(address: ShippingAddress) {
        check(isGift) { "선물 주문만 배송지를 나중에 지정할 수 있습니다." }
        check(shippingAddress == null) { "이미 배송지가 지정된 주문입니다." }
        shippingAddress = address
    }

    /** 하위 주문을 추가하고 양방향 연관관계를 맞춘다. */
    fun addSubOrder(subOrder: SubOrder) {
        subOrders.add(subOrder)
        subOrder.order = this
    }

    /**
     * 모든 SubOrder 가 확정된 뒤 금액 합계를 재계산한다(쿠폰/포인트는 Phase 4에서 차감 반영).
     * [deliveryFeeTotal] 은 상품합계([totalAmount])와 별개로 결제금액에 더해진다 — 배송비는 쿠폰/포인트
     * 할인 대상(상품가) 이 아니라는 판단([distributePayable] 참고). 정산은 subtotal 만 쓰므로 영향 없음.
     */
    fun recalculateAmounts() {
        totalAmount = subOrders.sumOf { it.subtotal }
        deliveryFeeTotal = subOrders.sumOf { it.deliveryFee }
        payableAmount = totalAmount + deliveryFeeTotal - discountAmount - pointUsed
    }

    /** 하위 주문 중 하나라도 발송/배송 단계에 들어갔는지 — 배송 시작 후에는 취소 불가. */
    val hasShipmentStarted: Boolean
        get() = subOrders.any {
            it.status == SubOrderStatus.SHIPPED || it.status == SubOrderStatus.DELIVERED
        }

    /** 모든 하위 주문이 취소되었는지(부분 취소 누적이 전체가 됐는지). */
    val isFullyCanceled: Boolean
        get() = subOrders.isNotEmpty() && subOrders.all { it.status == SubOrderStatus.CANCELED }

    /**
     * 최종 결제금액([payableAmount])을 하위 주문 상품합계 비례로 배분해 각 [SubOrder.payableShare] 에 저장한다.
     * 정수 나눗셈 잔액은 마지막 하위 주문에 몰아 합계 정합성을 보장한다. 쿠폰/포인트 적용 후 호출한다.
     *
     * 배송비([deliveryFeeTotal])는 상품합계 비례 배분 대상에서 제외하고, 각 SubOrder 자신의
     * [SubOrder.deliveryFee] 를 그대로 더한다(자기 슬롯의 추가요금은 자기 몫이 원칙).
     */
    fun distributePayable() {
        val subtotalSum = subOrders.sumOf { it.subtotal }
        val merchandiseNet = payableAmount - deliveryFeeTotal
        if (subtotalSum == 0L) {
            subOrders.forEach { it.payableShare = it.deliveryFee }
            return
        }
        var allocated = 0L
        subOrders.forEachIndexed { index, sub ->
            val merchandiseShare = if (index == subOrders.lastIndex) {
                merchandiseNet - allocated
            } else {
                (merchandiseNet * sub.subtotal / subtotalSum).also { allocated += it }
            }
            sub.payableShare = merchandiseShare + sub.deliveryFee
        }
    }
}
