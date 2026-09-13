package com.example.starter.domain.review

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.review.dto.ReviewResponse
import com.example.starter.domain.review.dto.ReviewableOrderItemResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 마이페이지 리뷰 API (인증 필요). 내가 쓴 리뷰 목록과 작성 가능한 주문 항목을 조회한다.
 */
@RestController
@RequestMapping("/api/me")
class MyReviewController(
    private val reviewService: ReviewService,
) {

    @GetMapping("/reviews")
    fun myReviews(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<ReviewResponse>> =
        ApiResponse.success(reviewService.getMyReviews(principal.userId, pageable))

    @GetMapping("/orders/reviewable")
    fun reviewableOrderItems(
        @AuthenticationPrincipal principal: CustomUserDetails,
    ): ApiResponse<List<ReviewableOrderItemResponse>> =
        ApiResponse.success(reviewService.getReviewableOrderItems(principal.userId))
}
