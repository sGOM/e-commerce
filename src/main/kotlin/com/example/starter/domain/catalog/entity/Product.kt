package com.example.starter.domain.catalog.entity

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
import jakarta.persistence.Table

/**
 * 상품. 마켓플레이스에서 반드시 한 [Seller] 에 속한다.
 *
 * 가격은 원(KRW) 단위 정수([basePrice])로 보관한다. 실제 판매가는 옵션의
 * [ProductOption.additionalPrice] 를 더한 값이며, 재고는 옵션 단위([ProductOption])로 관리한다.
 */
@Entity
@Table(name = "products")
class Product(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: Seller,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(name = "base_price", nullable = false)
    var basePrice: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.DRAFT,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    val options: MutableList<ProductOption> = mutableListOf()

    /** 옵션을 추가하고 양방향 연관관계를 맞춘다. */
    fun addOption(option: ProductOption) {
        options.add(option)
        option.product = this
    }
}
