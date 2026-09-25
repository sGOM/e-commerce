package com.example.starter.domain.delivery.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 기본 배송비 정책(ROADMAP 7.1). 단일 행을 유지하며 관리자가 금액을 런타임 변경한다.
 * 판매자(SubOrder) 단위로 [baseFee] 를 부과하고, 멤버십 무료배송 회원은 면제한다(OrderService 참고).
 */
@Entity
@Table(name = "shipping_policies")
class ShippingPolicy(
    @Column(name = "base_fee", nullable = false)
    var baseFee: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
