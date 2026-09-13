package com.example.starter.domain.coupon

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.coupon.dto.CouponResponse
import com.example.starter.domain.coupon.dto.CreateCouponRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 쿠폰 발행 API (`ROLE_ADMIN`).
 */
@RestController
@RequestMapping("/api/admin/coupons")
class AdminCouponController(
    private val adminCouponService: AdminCouponService,
) {

    @PostMapping
    fun create(@RequestBody @Valid request: CreateCouponRequest): ApiResponse<CouponResponse> =
        ApiResponse.success(adminCouponService.create(request), "쿠폰을 발행했습니다.")
}
