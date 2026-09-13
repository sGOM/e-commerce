package com.example.starter.domain.review

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.review.dto.ReviewRewardPolicyResponse
import com.example.starter.domain.review.dto.UpdateReviewRewardPolicyRequest
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 리뷰 정책 관리 API (`ROLE_ADMIN`). 적립 포인트/작성 가능 기간/신고 임계치를 배포 없이 변경한다.
 */
@RestController
@RequestMapping("/api/admin/review-policy")
@PreAuthorize("hasRole('ADMIN')")
class AdminReviewPolicyController(
    private val reviewRewardPolicyService: ReviewRewardPolicyService,
) {

    @GetMapping
    fun get(): ApiResponse<ReviewRewardPolicyResponse> =
        ApiResponse.success(reviewRewardPolicyService.getPolicy())

    @PatchMapping
    fun update(@RequestBody @Valid request: UpdateReviewRewardPolicyRequest): ApiResponse<ReviewRewardPolicyResponse> =
        ApiResponse.success(reviewRewardPolicyService.update(request), "정책이 변경되었습니다.")
}
