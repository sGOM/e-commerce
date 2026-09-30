package com.example.starter.domain.returns

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.returns.dto.CreateReturnRequest
import com.example.starter.domain.returns.dto.ReturnResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 회원 반품 요청·조회. */
@RestController
@RequestMapping("/api/me/returns")
class MyReturnController(
    private val returnService: ReturnService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<ReturnResponse>> =
        ApiResponse.success(returnService.getMyReturns(principal.userId))

    @PostMapping
    fun request(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateReturnRequest,
    ): ApiResponse<ReturnResponse> =
        ApiResponse.success(returnService.request(principal.userId, request), "반품을 요청했습니다.")
}
