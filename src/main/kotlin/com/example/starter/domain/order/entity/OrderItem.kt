package com.example.starter.domain.order.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * 주문 항목 — 주문 시점의 가격/상품명/옵션을 **스냅샷**으로 보관한다(원본 변경과 무관).
 *
 * [optionId] 는 재고 복원·추적용 참조이며, 표시 정보는 모두 스냅샷 컬럼을 사용한다.
 */
@Entity
@Table(name = "order_items")
class OrderItem(
    @Column(name = "option_id", nullable = false)
    val optionId: Long,

    @Column(name = "product_name", nullable = false, length = 200)
    val productName: String,

    @Column(name = "option_name", nullable = false, length = 200)
    val optionName: String,

    @Column(name = "unit_price", nullable = false)
    val unitPrice: Long,

    @Column(nullable = false)
    val quantity: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_order_id", nullable = false)
    lateinit var subOrder: SubOrder

    @Column(name = "line_total", nullable = false)
    val lineTotal: Long = unitPrice * quantity
}
