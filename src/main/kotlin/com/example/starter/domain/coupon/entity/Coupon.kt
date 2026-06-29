package com.example.starter.domain.coupon.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 쿠폰 정의. 발급(IssuedCoupon)의 원본이며 할인 규칙을 보유한다.
 *
 * 할인 계산은 전부 서버에서 수행한다([calculateDiscount]). 정률은 [maxDiscountAmount] 로 상한을 둘 수 있고,
 * 어떤 경우에도 주문 상품합계를 초과하지 않는다.
 */
@Entity
@Table(name = "coupons")
class Coupon(
    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    val discountType: DiscountType,

    @Column(name = "discount_value", nullable = false)
    val discountValue: Long, // RATE: 퍼센트, FIXED: 원

    @Column(name = "min_order_amount", nullable = false)
    val minOrderAmount: Long = 0,

    @Column(name = "max_discount_amount")
    val maxDiscountAmount: Long? = null, // 정률 할인 상한

    @Column(name = "valid_from", nullable = false)
    val validFrom: Instant,

    @Column(name = "valid_until", nullable = false)
    val validUntil: Instant,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    fun isValidAt(at: Instant): Boolean = !at.isBefore(validFrom) && !at.isAfter(validUntil)

    fun meetsMinOrderAmount(orderAmount: Long): Boolean = orderAmount >= minOrderAmount

    /** 주문 상품합계에 대한 실제 할인액(주문금액 초과 불가, 정률은 상한 적용). */
    fun calculateDiscount(orderAmount: Long): Long {
        val raw = when (discountType) {
            DiscountType.RATE -> orderAmount * discountValue / 100
            DiscountType.FIXED -> discountValue
        }
        val capped = maxDiscountAmount?.let { minOf(raw, it) } ?: raw
        return minOf(capped, orderAmount)
    }
}
