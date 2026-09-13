package com.example.starter.domain.review.dto

import com.example.starter.domain.review.entity.Review
import com.example.starter.domain.review.entity.ReviewStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

/** 리뷰 작성 요청 — 구매 인증은 [orderItemId] 로 서버가 검증한다(AC1~AC4). */
data class CreateReviewRequest(
    @field:NotNull
    val orderItemId: Long?,
    @field:NotNull @field:Min(1) @field:Max(5)
    val rating: Int?,
    @field:NotBlank @field:Size(min = 10, max = 2000)
    val content: String?,
    @field:Size(max = 5)
    val imageUrls: List<String> = emptyList(),
)

/** 리뷰 수정 요청 — 전체 교체(평점/본문/이미지 목록). */
data class UpdateReviewRequest(
    @field:NotNull @field:Min(1) @field:Max(5)
    val rating: Int?,
    @field:NotBlank @field:Size(min = 10, max = 2000)
    val content: String?,
    @field:Size(max = 5)
    val imageUrls: List<String> = emptyList(),
)

/** 리뷰 신고 요청. */
data class ReportReviewRequest(
    @field:NotBlank @field:Size(max = 500)
    val reason: String?,
)

/** 상품 리뷰 목록 정렬 기준(AC9). photoOnly 는 별도 필터 플래그. */
enum class ReviewSort {
    LATEST,
    RATING_DESC,
}

/** 상품 리뷰 목록 조회 조건(공개 API). */
data class ReviewSearchCondition(
    val sort: ReviewSort = ReviewSort.LATEST,
    val photoOnly: Boolean = false,
)

/** 관리자 리뷰 검색 조건. [reported] = true 면 신고 검토 큐([ReviewStatus.REPORTED])만 조회한다. */
data class AdminReviewSearchCondition(
    val productId: Long? = null,
    val userId: Long? = null,
    val status: ReviewStatus? = null,
    val reported: Boolean? = null,
)

/** 관리자 리뷰 상태 변경 요청 — VISIBLE(복구)/HIDDEN(숨김) 만 허용한다(REPORTED 는 자동 전이 전용). */
data class AdminReviewStatusRequest(
    @field:NotNull
    val status: ReviewStatus?,
)

/** 리뷰 응답. [authorName] 은 공개 목록에서는 마스킹, 본인/관리자 조회에서는 원문을 노출한다. */
data class ReviewResponse(
    val id: Long,
    val orderItemId: Long,
    val productId: Long,
    val userId: Long,
    val authorName: String,
    val rating: Int,
    val content: String,
    val status: ReviewStatus,
    val reportCount: Int,
    val imageUrls: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(review: Review, maskAuthor: Boolean) = ReviewResponse(
            id = requireNotNull(review.id),
            orderItemId = review.orderItemId,
            productId = review.productId,
            userId = review.userId,
            authorName = if (maskAuthor) mask(review.authorName) else review.authorName,
            rating = review.rating,
            content = review.content,
            status = review.status,
            reportCount = review.reportCount,
            imageUrls = review.images.sortedBy { it.displayOrder }.map { it.imageUrl },
            createdAt = review.createdAt,
            updatedAt = review.updatedAt,
        )

        private fun mask(name: String): String =
            if (name.length <= 1) "*" else name.first() + "*".repeat(name.length - 1)
    }
}

/** 리뷰 작성 대기 주문 항목(마이페이지) 응답. */
data class ReviewableOrderItemResponse(
    val orderItemId: Long,
    val subOrderId: Long,
    val productId: Long,
    val productName: String,
    val optionName: String,
    val deliveredAt: Instant?,
)

/** 리뷰 정책 응답(관리자). */
data class ReviewRewardPolicyResponse(
    val textReviewPoint: Long,
    val photoReviewPoint: Long,
    val reviewableDays: Int,
    val reportThreshold: Int,
)

/** 리뷰 정책 변경 요청(관리자). 전달한 항목만 변경한다(부분 업데이트). */
data class UpdateReviewRewardPolicyRequest(
    @field:PositiveOrZero val textReviewPoint: Long? = null,
    @field:PositiveOrZero val photoReviewPoint: Long? = null,
    @field:Positive val reviewableDays: Int? = null,
    @field:Positive val reportThreshold: Int? = null,
)
