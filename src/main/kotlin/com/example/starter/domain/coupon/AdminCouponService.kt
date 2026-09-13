package com.example.starter.domain.coupon

import com.example.starter.domain.coupon.dto.CouponResponse
import com.example.starter.domain.coupon.dto.CreateCouponRequest
import com.example.starter.domain.coupon.entity.Coupon
import com.example.starter.domain.coupon.entity.IssuedCoupon
import com.example.starter.domain.coupon.repository.CouponRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 쿠폰 발행. 쿠폰 정의를 만들고, 지정한 회원들에게 발급분(IssuedCoupon)을 생성한다.
 */
@Service
@Transactional(readOnly = true)
class AdminCouponService(
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {

    @Transactional
    fun create(request: CreateCouponRequest): CouponResponse {
        val coupon = couponRepository.save(
            Coupon(
                name = request.name!!,
                discountType = request.discountType!!,
                discountValue = request.discountValue!!,
                minOrderAmount = request.minOrderAmount,
                maxDiscountAmount = request.maxDiscountAmount,
                validFrom = request.validFrom!!,
                validUntil = request.validUntil!!,
            ),
        )
        request.issueToUserIds.distinct().forEach { userId ->
            issuedCouponRepository.save(IssuedCoupon(coupon = coupon, userId = userId))
        }
        return CouponResponse.from(coupon, request.issueToUserIds.distinct().size)
    }
}
