package com.example.starter.domain.review

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.review.dto.CreateReviewRequest
import com.example.starter.domain.review.dto.ReportReviewRequest
import com.example.starter.domain.review.dto.ReviewResponse
import com.example.starter.domain.review.dto.UpdateReviewRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 리뷰 작성/수정/삭제/신고 API (인증 필요). 조회는 [ProductReviewController]/[MyReviewController] 참고.
 */
@RestController
@RequestMapping("/api/reviews")
class ReviewController(
    private val reviewService: ReviewService,
) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateReviewRequest,
    ): ApiResponse<ReviewResponse> =
        ApiResponse.success(reviewService.create(principal.userId, request), "리뷰가 등록되었습니다.")

    @PatchMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateReviewRequest,
    ): ApiResponse<ReviewResponse> =
        ApiResponse.success(reviewService.update(principal.userId, id, request), "리뷰가 수정되었습니다.")

    @DeleteMapping("/{id}")
    fun delete(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        reviewService.delete(principal.userId, id)
        return ApiResponse.success("리뷰가 삭제되었습니다.")
    }

    @PostMapping("/{id}/reports")
    fun report(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
        @RequestBody @Valid request: ReportReviewRequest,
    ): ApiResponse<Unit> {
        reviewService.report(principal.userId, id, request)
        return ApiResponse.success("신고가 접수되었습니다.")
    }
}
