package com.example.starter.domain.restock

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.restock.dto.RestockAlertResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 마이페이지 재입고 알림 신청 목록 API (인증 필요).
 */
@RestController
@RequestMapping("/api/me/restock-alerts")
class MyRestockAlertController(
    private val restockAlertService: RestockAlertService,
) {

    @GetMapping
    fun myAlerts(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<RestockAlertResponse>> =
        ApiResponse.success(restockAlertService.getMyAlerts(principal.userId, pageable))
}
