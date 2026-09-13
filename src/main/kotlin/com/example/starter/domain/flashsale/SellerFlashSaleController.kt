package com.example.starter.domain.flashsale

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.flashsale.dto.CreateFlashSaleRequest
import com.example.starter.domain.flashsale.dto.FlashSaleResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 판매자 타임딜 신청 API (`ROLE_SELLER`). 본인 상점 옵션만 등록할 수 있다(오픈 이슈 #1 결정,
 * [FlashSaleService] 문서 참고 — 승인 대기 없이 즉시 활성화되며 관리자가 사후 강제 종료할 수 있다).
 */
@RestController
@RequestMapping("/api/seller/flash-sales")
class SellerFlashSaleController(
    private val flashSaleService: FlashSaleService,
) {

    @GetMapping
    fun getMyFlashSales(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<FlashSaleResponse>> =
        ApiResponse.success(flashSaleService.listForSeller(principal.userId))

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateFlashSaleRequest,
    ): ApiResponse<FlashSaleResponse> =
        ApiResponse.success(flashSaleService.createForSeller(principal.userId, request), "타임딜을 등록했습니다.")
}
