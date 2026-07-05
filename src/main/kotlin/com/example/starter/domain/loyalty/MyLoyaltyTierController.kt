package com.example.starter.domain.loyalty

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.loyalty.dto.MyLoyaltyTierResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 마이페이지 로열티 등급 조회 API (인증 필요). */
@RestController
@RequestMapping("/api/me/loyalty-tier")
class MyLoyaltyTierController(
    private val loyaltyTierService: LoyaltyTierService,
) {

    @GetMapping
    fun myTier(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<MyLoyaltyTierResponse> =
        ApiResponse.success(loyaltyTierService.getMyTier(principal.userId))
}
