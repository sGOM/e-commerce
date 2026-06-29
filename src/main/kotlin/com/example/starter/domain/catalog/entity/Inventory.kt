package com.example.starter.domain.catalog.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

/**
 * 옵션(SKU)별 재고. 재고 정합성의 **단일 진실 공급원**.
 *
 * [quantity] 총 보유 수량, [reserved] 주문 진행중 예약분. 실제 가용 재고는 [available].
 * 동시성 제어를 통한 차감/복원 로직은 Phase 3(주문)에서 추가한다.
 */
@Entity
@Table(name = "inventories")
class Inventory(
    @Column(nullable = false)
    var quantity: Int = 0,

    @Column(nullable = false)
    var reserved: Int = 0,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @OneToOne(optional = false)
    @JoinColumn(name = "option_id", nullable = false, unique = true)
    lateinit var option: ProductOption

    /** 실제 가용(구매 가능) 재고 수량 */
    val available: Int
        get() = quantity - reserved
}
