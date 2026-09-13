package com.example.starter.domain.promotion.entity

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
 * 컬렉션에 편성된 상품 1건. 같은 상품이 여러 컬렉션에 동시에 속할 수 있다(제약 없음, 기획서 §4).
 * [productId] 는 catalog 애그리텟(Product) 을 참조만 하는 순수 id — 상품 상태 변경 시 이 테이블은
 * 동기화하지 않고, 고객 노출 조회 시점에 [com.example.starter.domain.catalog.entity.ProductStatus] 를
 * 필터링한다(AC7).
 */
@Entity
@Table(name = "collection_products")
class CollectionProduct(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id", nullable = false)
    lateinit var collection: Collection
}
