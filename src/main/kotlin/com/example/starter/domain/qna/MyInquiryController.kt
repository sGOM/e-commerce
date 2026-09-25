package com.example.starter.domain.qna

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.qna.dto.CreateInquiryRequest
import com.example.starter.domain.qna.dto.InquiryResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 회원의 상품 비밀 문의 작성·조회 — 본인 문의만 보인다. */
@RestController
@RequestMapping("/api/me/inquiries")
class MyInquiryController(
    private val qnaService: ProductQnaService,
) {

    @PostMapping
    fun ask(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateInquiryRequest,
    ): ApiResponse<InquiryResponse> =
        ApiResponse.success(qnaService.ask(principal.userId, request.productId!!, request.question!!), "문의가 등록되었습니다.")

    @GetMapping
    fun mine(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) productId: Long?,
    ): ApiResponse<List<InquiryResponse>> = ApiResponse.success(qnaService.myInquiries(principal.userId, productId))
}
