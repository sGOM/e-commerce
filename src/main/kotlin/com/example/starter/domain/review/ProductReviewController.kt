package com.example.starter.domain.review

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.review.dto.ReviewResponse
import com.example.starter.domain.review.dto.ReviewSearchCondition
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 리뷰 공개 조회 API. 인증 불필요(게스트도 열람 가능) — `/api/products` 하위는 공개 경로다.
 */
@RestController
@RequestMapping("/api/products/{productId}/reviews")
class ProductReviewController(
    private val reviewService: ReviewService,
) {

    @GetMapping
    fun list(
        @PathVariable productId: Long,
        condition: ReviewSearchCondition,
        @PageableDefault(size = 10) pageable: Pageable,
    ): ApiResponse<PageResponse<ReviewResponse>> =
        ApiResponse.success(reviewService.listByProduct(productId, condition, pageable))
}
