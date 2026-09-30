package com.example.starter.domain.coupon.dto

import com.example.starter.domain.coupon.entity.Coupon
import com.example.starter.domain.coupon.entity.DiscountType
import com.example.starter.domain.coupon.entity.IssuedCoupon
import java.time.Instant

/** 내 쿠폰 응답 */
data class IssuedCouponResponse(
    val issuedCouponId: Long,
    val name: String,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long,
    val maxDiscountAmount: Long?,
    val validFrom: Instant,
    val validUntil: Instant,
    val used: Boolean,
) {
    companion object {
        fun from(issued: IssuedCoupon): IssuedCouponResponse {
            val coupon = issued.coupon
            return IssuedCouponResponse(
                issuedCouponId = requireNotNull(issued.id),
                name = coupon.name,
                discountType = coupon.discountType,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                maxDiscountAmount = coupon.maxDiscountAmount,
                validFrom = coupon.validFrom,
                validUntil = coupon.validUntil,
                used = issued.used,
            )
        }
    }
}

/** 멤버십 전용 쿠폰(AC10) — 받을 수 있는 목록. [claimed] 는 이미 받았는지. */
data class MembershipCouponResponse(
    val couponId: Long,
    val name: String,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long,
    val maxDiscountAmount: Long?,
    val validUntil: Instant,
    val claimed: Boolean,
) {
    companion object {
        fun from(coupon: Coupon, claimed: Boolean) = MembershipCouponResponse(
            couponId = requireNotNull(coupon.id),
            name = coupon.name,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            minOrderAmount = coupon.minOrderAmount,
            maxDiscountAmount = coupon.maxDiscountAmount,
            validUntil = coupon.validUntil,
            claimed = claimed,
        )
    }
}
