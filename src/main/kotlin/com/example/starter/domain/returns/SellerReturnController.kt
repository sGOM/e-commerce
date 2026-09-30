package com.example.starter.domain.returns

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.returns.dto.ReturnResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 판매자 반품 처리 (`ROLE_SELLER`). */
@RestController
@RequestMapping("/api/seller/returns")
class SellerReturnController(
    private val sellerReturnService: SellerReturnService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<ReturnResponse>> =
        ApiResponse.success(sellerReturnService.getMyReturns(principal.userId))

    @PostMapping("/{returnId}/approve")
    fun approve(@AuthenticationPrincipal principal: CustomUserDetails, @PathVariable returnId: Long): ApiResponse<ReturnResponse> =
        ApiResponse.success(sellerReturnService.approve(principal.userId, returnId), "반품을 승인하고 회수를 시작했습니다.")

    @PostMapping("/{returnId}/complete")
    fun complete(@AuthenticationPrincipal principal: CustomUserDetails, @PathVariable returnId: Long): ApiResponse<ReturnResponse> =
        ApiResponse.success(sellerReturnService.complete(principal.userId, returnId), "검수를 완료하고 환불했습니다.")

    @PostMapping("/{returnId}/reject")
    fun reject(@AuthenticationPrincipal principal: CustomUserDetails, @PathVariable returnId: Long): ApiResponse<ReturnResponse> =
        ApiResponse.success(sellerReturnService.reject(principal.userId, returnId), "반품을 거절했습니다.")
}
