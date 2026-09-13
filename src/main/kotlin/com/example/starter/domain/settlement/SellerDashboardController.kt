package com.example.starter.domain.settlement

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.settlement.dto.SellerDashboardResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 판매자 매출 대시보드 API (`ROLE_SELLER`). `from`/`to` 는 `yyyy-MM-dd`, 생략 시 최근 30일.
 */
@RestController
@RequestMapping("/api/seller/dashboard")
class SellerDashboardController(
    private val sellerDashboardService: SellerDashboardService,
) {

    @GetMapping
    fun get(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ApiResponse<SellerDashboardResponse> =
        ApiResponse.success(sellerDashboardService.get(principal.userId, from, to))
}
