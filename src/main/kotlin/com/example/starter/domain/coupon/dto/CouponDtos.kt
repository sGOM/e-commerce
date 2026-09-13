package com.example.starter.domain.coupon.dto

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
