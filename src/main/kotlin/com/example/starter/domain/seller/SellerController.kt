package com.example.starter.domain.seller

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.seller.dto.SellerApplyRequest
import com.example.starter.domain.seller.dto.SellerResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 판매자 입점/상점 API. 입점 신청은 일반 회원이, 상점 조회는 승인된 판매자가 사용한다.
 */
@RestController
@RequestMapping("/api/seller")
class SellerController(
    private val sellerService: SellerService,
) {

    /** 입점 신청 — 로그인한 회원이면 누구나(아직 ROLE_SELLER 아님). */
    @PostMapping("/apply")
    fun apply(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: SellerApplyRequest,
    ): ApiResponse<SellerResponse> =
        ApiResponse.success(
            sellerService.apply(principal.userId, request.storeName!!, request.description),
            "입점 신청이 접수되었습니다.",
        )

    /** 내 상점 조회 — 승인된 판매자(ROLE_SELLER). */
    @GetMapping("/store")
    fun myStore(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<SellerResponse> =
        ApiResponse.success(sellerService.getMyStore(principal.userId))
}
