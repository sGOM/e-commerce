package com.example.starter.domain.coupon.dto

import com.example.starter.domain.coupon.entity.Coupon
import com.example.starter.domain.coupon.entity.DiscountType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

/** 쿠폰 발행 요청(관리자). 정의를 만들고, 선택적으로 특정 회원에게 즉시 발급한다. */
data class CreateCouponRequest(
    @field:NotNull val name: String?,
    @field:NotNull val discountType: DiscountType?,
    @field:NotNull @field:PositiveOrZero val discountValue: Long?,
    @field:PositiveOrZero val minOrderAmount: Long = 0,
    val maxDiscountAmount: Long? = null,
    @field:NotNull val validFrom: Instant?,
    @field:NotNull val validUntil: Instant?,
    /** 발급 대상 회원. 비우면 정의만 생성한다. */
    val issueToUserIds: List<Long> = emptyList(),
)

/** 쿠폰 정의 응답 */
data class CouponResponse(
    val couponId: Long,
    val name: String,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long,
    val maxDiscountAmount: Long?,
    val validFrom: Instant,
    val validUntil: Instant,
    val issuedCount: Int,
) {
    companion object {
        fun from(coupon: Coupon, issuedCount: Int) = CouponResponse(
            couponId = requireNotNull(coupon.id),
            name = coupon.name,
            discountType = coupon.discountType,
            discountValue = coupon.discountValue,
            minOrderAmount = coupon.minOrderAmount,
            maxDiscountAmount = coupon.maxDiscountAmount,
            validFrom = coupon.validFrom,
            validUntil = coupon.validUntil,
            issuedCount = issuedCount,
        )
    }
}
