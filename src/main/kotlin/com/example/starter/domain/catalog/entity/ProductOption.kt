package com.example.starter.domain.catalog.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/**
 * 상품 옵션(SKU). 판매·재고의 실제 단위(예: "블랙 / L").
 *
 * 최종 판매가 = 상품 [Product.basePrice] + [additionalPrice]. 재고는 [inventory] 1:1 로 관리한다.
 */
@Entity
@Table(name = "product_options")
class ProductOption(
    @Column(nullable = false, length = 200)
    var name: String,

    @Column(nullable = false, length = 100, unique = true)
    var sku: String,

    @Column(name = "additional_price", nullable = false)
    var additionalPrice: Long = 0,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    lateinit var product: Product

    @OneToOne(mappedBy = "option", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var inventory: Inventory? = null

    /** 재고를 연결하고 양방향 연관관계를 맞춘다. */
    fun assignInventory(inventory: Inventory) {
        this.inventory = inventory
        inventory.option = this
    }
}
