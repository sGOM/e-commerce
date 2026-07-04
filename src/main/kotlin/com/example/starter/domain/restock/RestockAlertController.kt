package com.example.starter.domain.restock

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.restock.dto.RestockAlertResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 옵션 단위 재입고 알림 신청/취소 API (회원 전용). 조회는 [MyRestockAlertController] 참고.
 * `/api/products/` 하위 경로는 SecurityConfig 에서 공개(permitAll) 처리되어 있으나, 이 경로는
 * 그보다 먼저 매칭되는 명시적 규칙으로 인증을 요구한다.
 */
@RestController
@RequestMapping("/api/products/options/{optionId}/restock-alerts")
class RestockAlertController(
    private val restockAlertService: RestockAlertService,
) {

    @PostMapping
    fun subscribe(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable optionId: Long,
    ): ApiResponse<RestockAlertResponse> =
        ApiResponse.success(restockAlertService.subscribe(principal.userId, optionId), "재입고 알림을 신청했습니다.")

    @DeleteMapping
    fun cancel(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable optionId: Long,
    ): ApiResponse<Unit> {
        restockAlertService.cancel(principal.userId, optionId)
        return ApiResponse.success("재입고 알림 신청을 취소했습니다.")
    }
}
