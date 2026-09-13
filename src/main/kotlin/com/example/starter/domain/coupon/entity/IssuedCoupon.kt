package com.example.starter.domain.coupon.entity

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
import java.time.Instant

/**
 * 회원에게 발급된 쿠폰(1회용). 주문에 적용되면 [used] 가 되고, 결제 실패/취소 시 [restore] 로 되돌린다.
 */
@Entity
@Table(name = "issued_coupons")
class IssuedCoupon(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    val coupon: Coupon,

    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false)
    var used: Boolean = false

    @Column(name = "used_at")
    var usedAt: Instant? = null

    @Column(name = "order_id")
    var orderId: Long? = null

    /** 주문에 적용(사용 처리). */
    fun use(orderId: Long) {
        used = true
        usedAt = Instant.now()
        this.orderId = orderId
    }

    /** 미사용 상태로 복원(결제 실패/주문 취소). */
    fun restore() {
        used = false
        usedAt = null
        orderId = null
    }
}
