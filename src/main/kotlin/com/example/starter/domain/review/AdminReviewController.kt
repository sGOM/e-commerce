package com.example.starter.domain.review

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.review.dto.AdminReviewSearchCondition
import com.example.starter.domain.review.dto.AdminReviewStatusRequest
import com.example.starter.domain.review.dto.ReviewResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 리뷰 검수 API (`ROLE_ADMIN`). 검색(상품/작성자/상태/신고여부) 및 숨김·복구 처리(AC13).
 */
@RestController
@RequestMapping("/api/admin/reviews")
@PreAuthorize("hasRole('ADMIN')")
class AdminReviewController(
    private val adminReviewService: AdminReviewService,
) {

    @GetMapping
    fun search(
        condition: AdminReviewSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<ReviewResponse>> =
        ApiResponse.success(adminReviewService.search(condition, pageable))

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: AdminReviewStatusRequest,
    ): ApiResponse<ReviewResponse> =
        ApiResponse.success(adminReviewService.changeStatus(id, request.status!!), "리뷰 상태가 변경되었습니다.")
}
