package com.example.starter.domain.coupon

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.coupon.dto.MembershipCouponResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 멤버십 전용 쿠폰 목록·수령(회원). */
@RestController
@RequestMapping("/api/me/membership/coupons")
class MyMembershipCouponController(
    private val membershipCouponService: MembershipCouponService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<MembershipCouponResponse>> =
        ApiResponse.success(membershipCouponService.getClaimable(principal.userId))

    @PostMapping("/{couponId}/claim")
    fun claim(@AuthenticationPrincipal principal: CustomUserDetails, @PathVariable couponId: Long): ApiResponse<Unit> {
        membershipCouponService.claim(principal.userId, couponId)
        return ApiResponse.success("쿠폰을 받았습니다.")
    }
}
