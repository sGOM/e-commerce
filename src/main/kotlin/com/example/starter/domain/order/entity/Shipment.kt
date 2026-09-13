package com.example.starter.domain.order.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * 배송 송장 — [SubOrder] 단위. 판매자가 택배사/송장번호를 등록하면 생성된다.
 */
@Entity
@Table(name = "shipments")
class Shipment(
    @Column(nullable = false, length = 50)
    val courier: String,

    @Column(name = "tracking_number", nullable = false, length = 100)
    val trackingNumber: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_order_id", nullable = false, unique = true)
    lateinit var subOrder: SubOrder

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ShipmentStatus = ShipmentStatus.SHIPPED

    @Column(name = "shipped_at")
    var shippedAt: Instant? = Instant.now()
}
