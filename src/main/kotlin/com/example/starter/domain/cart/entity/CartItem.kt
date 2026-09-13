package com.example.starter.domain.cart.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.domain.catalog.entity.ProductOption
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 장바구니 항목. 옵션(SKU) 단위로 담는다. 가격은 보관하지 않고 **조회 시점 현재가**를 사용한다
 * (스냅샷은 주문 시점에만 — Phase 3).
 */
@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = [UniqueConstraint(name = "uq_cart_option", columnNames = ["cart_id", "option_id"])],
)
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    val cart: Cart,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_id", nullable = false)
    val option: ProductOption,

    @Column(nullable = false)
    var quantity: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
