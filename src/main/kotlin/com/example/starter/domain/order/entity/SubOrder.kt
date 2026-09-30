package com.example.starter.domain.order.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.domain.seller.entity.Seller
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 하위 주문 — 판매자 단위. 배송·취소·정산이 이 단위로 일어난다.
 * 한 [Order] 에 여러 판매자 상품이 섞이면 판매자 수만큼 SubOrder 가 생성된다.
 */
@Entity
@Table(name = "sub_orders")
class SubOrder(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: Seller,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    lateinit var order: Order

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SubOrderStatus = SubOrderStatus.CREATED

    @Column(nullable = false)
    var subtotal: Long = 0

    // 이 하위 주문의 실제 결제 기여액(쿠폰/포인트 차감 반영). 부분 환불 단위.
    @Column(name = "payable_share", nullable = false)
    var payableShare: Long = 0

    // 정산서 id(NULL = 미정산). 셀러 정산 시 채워진다.
    @Column(name = "settlement_id")
    var settlementId: Long? = null

    // 구매자 수령 확인(구매확정) 시각. 리뷰 작성 가능 기간(N일) 계산의 기준점.
    @Column(name = "delivered_at")
    var deliveredAt: Instant? = null

    // 선택한 배송 슬롯(`docs/planning/delivery-slot.md`). 정원 증감은 재고 reserved 와 동일하게
    // DeliverySlotRepository 원자적 UPDATE 로만 관리하며, 이 컬럼은 참조 id 만 스냅샷한다.
    @Column(name = "delivery_slot_id")
    var deliverySlotId: Long? = null

    // 배송비 스냅샷(원) = 기본 배송비(ShippingPolicy, 판매자 단위) + 슬롯 추가요금
    // ([com.example.starter.domain.order.entity.Order.recalculateAmounts]/[distributePayable] 참고).
    // 판매자가 자기 택배사로 내는 실비라 정산에서 수수료 없이 판매자에게 지급한다(ROADMAP 7.3).
    @Column(name = "delivery_fee", nullable = false)
    var deliveryFee: Long = 0

    // 단순변심 반품 완료 시 고객 환불에서 뺀 반품 배송비 — 회수 택배비를 낸 판매자 몫(7.3). 그 외 0.
    @Column(name = "seller_return_fee", nullable = false)
    var sellerReturnFee: Long = 0

    /** 수수료를 매기는 판매액 — 반품 완료 건은 판매가 없던 것으로 본다. */
    val settlementSales: Long
        get() = if (status == SubOrderStatus.RETURNED) 0 else subtotal

    /** 수수료 없이 판매자에게 주는 배송비 — 반품 완료 건은 고객이 낸 반품 배송비만. */
    val settlementDeliveryFee: Long
        get() = if (status == SubOrderStatus.RETURNED) sellerReturnFee else deliveryFee

    @OneToMany(mappedBy = "subOrder", cascade = [CascadeType.ALL], orphanRemoval = true)
    val items: MutableList<OrderItem> = mutableListOf()

    @OneToOne(mappedBy = "subOrder", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var shipment: Shipment? = null

    /** 주문 항목을 추가하고 양방향 연관관계를 맞춘다. */
    fun addItem(item: OrderItem) {
        items.add(item)
        item.subOrder = this
    }

    /** 항목 합계로 소계를 재계산한다. */
    fun recalculateSubtotal() {
        subtotal = items.sumOf { it.lineTotal }
    }

    /**
     * 결제 완료/상품준비 상태에서만 발송 가능. 배송지가 아직 없으면(선물 주문의 미수락 상태,
     * `docs/planning/gift-order.md` §9 오픈이슈 #1) 발송을 막는다 — 배송지 확정 전 SHIPPED 전이 가드.
     */
    val isShippable: Boolean
        get() = (status == SubOrderStatus.PAID || status == SubOrderStatus.PREPARING) && order.shippingAddress != null

    /** 송장을 등록하고 SHIPPED 로 전이한다. */
    fun ship(courier: String, trackingNumber: String) {
        val newShipment = Shipment(courier = courier, trackingNumber = trackingNumber)
        newShipment.subOrder = this
        shipment = newShipment
        status = SubOrderStatus.SHIPPED
    }

    /** 구매자가 수령을 확인(구매확정)하고 DELIVERED 로 전이한다. 발송 상태에서만 가능. */
    fun confirmDelivery() {
        status = SubOrderStatus.DELIVERED
        deliveredAt = Instant.now()
    }
}
