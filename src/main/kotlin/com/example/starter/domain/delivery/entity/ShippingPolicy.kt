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
 * 반품 정책값(단순변심 반품 배송비·구매확정 후 반품 가능 일수, ROADMAP 1.5)도 이 행에 둔다.
 */
@Entity
@Table(name = "shipping_policies")
class ShippingPolicy(
    @Column(name = "base_fee", nullable = false)
    var baseFee: Long,

    @Column(name = "return_fee", nullable = false)
    var returnFee: Long = 6_000,

    @Column(name = "return_window_days", nullable = false)
    var returnWindowDays: Int = 7,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
