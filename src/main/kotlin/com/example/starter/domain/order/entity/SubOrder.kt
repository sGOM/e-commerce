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

    /** 결제 완료/상품준비 상태에서만 발송 가능. */
    val isShippable: Boolean
        get() = status == SubOrderStatus.PAID || status == SubOrderStatus.PREPARING

    /** 송장을 등록하고 SHIPPED 로 전이한다. */
    fun ship(courier: String, trackingNumber: String) {
        val newShipment = Shipment(courier = courier, trackingNumber = trackingNumber)
        newShipment.subOrder = this
        shipment = newShipment
        status = SubOrderStatus.SHIPPED
    }
}
