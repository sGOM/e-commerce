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
 *
 * [unitPrice] 는 항상 주문 시점 **정가**(기본가+옵션추가금) 스냅샷이다. 타임딜(한정특가,
 * `docs/planning/flash-sale.md`)이 적용된 항목은 [flashSaleId]/[appliedSalePrice] 가 채워지며,
 * 실제 청구액([lineTotal])은 [appliedSalePrice] 를 우선 사용한다(없으면 정가 그대로 청구).
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

    @Column(name = "flash_sale_id")
    val flashSaleId: Long? = null,

    @Column(name = "applied_sale_price")
    val appliedSalePrice: Long? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_order_id", nullable = false)
    lateinit var subOrder: SubOrder

    @Column(name = "line_total", nullable = false)
    val lineTotal: Long = (appliedSalePrice ?: unitPrice) * quantity

    // 리뷰 적립 포인트를 이미 지급했는지(어뷰징 방지 — 리뷰 삭제/재작성과 무관하게 최초 1회만 지급).
    @Column(name = "review_rewarded", nullable = false)
    var reviewRewarded: Boolean = false

    /** 리뷰 최초 작성 시 적립 지급 처리를 표시한다(멱등 마킹, 되돌리지 않음). */
    fun markReviewRewarded() {
        reviewRewarded = true
    }
}
