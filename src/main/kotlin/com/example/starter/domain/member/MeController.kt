package com.example.starter.domain.member

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.coupon.CouponService
import com.example.starter.domain.coupon.dto.IssuedCouponResponse
import com.example.starter.domain.point.PointService
import com.example.starter.domain.point.dto.PointSummaryResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 내 정보(회원 전용) — 보유 쿠폰/포인트 조회.
 */
@RestController
@RequestMapping("/api/me")
class MeController(
    private val couponService: CouponService,
    private val pointService: PointService,
) {

    @GetMapping("/coupons")
    fun coupons(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<IssuedCouponResponse>> =
        ApiResponse.success(couponService.getMyCoupons(principal.userId))

    @GetMapping("/points")
    fun points(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<PointSummaryResponse> =
        ApiResponse.success(pointService.getMyPoints(principal.userId))
}
