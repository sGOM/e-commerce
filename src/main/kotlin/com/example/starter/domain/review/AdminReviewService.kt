package com.example.starter.domain.review

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.review.dto.AdminReviewSearchCondition
import com.example.starter.domain.review.dto.ReviewResponse
import com.example.starter.domain.review.entity.Review
import com.example.starter.domain.review.entity.ReviewStatus
import com.example.starter.domain.review.repository.ReviewRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 관리자 리뷰 검수. 상품/작성자/상태/신고여부로 검색하고 숨김·복구 처리한다(AC13).
 */
@Service
@Transactional(readOnly = true)
class AdminReviewService(
    private val reviewRepository: ReviewRepository,
    private val productRepository: ProductRepository,
) {

    fun search(condition: AdminReviewSearchCondition, pageable: Pageable): PageResponse<ReviewResponse> {
        // reported=true 는 신고 검토 큐(REPORTED)만 조회 — status 파라미터보다 우선한다.
        val statusFilter = if (condition.reported == true) ReviewStatus.REPORTED else condition.status
        val page = reviewRepository.findPage(pageable) {
            select(entity(Review::class))
                .from(entity(Review::class))
                .whereAnd(
                    condition.productId?.let { path(Review::productId).eq(it) },
                    condition.userId?.let { path(Review::userId).eq(it) },
                    statusFilter?.let { path(Review::status).eq(it) },
                )
                .orderBy(path(Review::id).desc())
        }
        return PageResponse.of(page) { ReviewResponse.from(it!!, maskAuthor = false) }
    }

    /** 숨김/복구만 허용한다(REPORTED 는 신고 누적으로만 전이하는 자동 상태). */
    @Transactional
    fun changeStatus(reviewId: Long, status: ReviewStatus): ReviewResponse {
        val review = reviewRepository.findById(reviewId).orElseThrow { BusinessException(ErrorCode.REVIEW_NOT_FOUND) }
        when (status) {
            ReviewStatus.HIDDEN -> review.hide()
            ReviewStatus.VISIBLE -> review.restore()
            ReviewStatus.REPORTED -> throw BusinessException(ErrorCode.INVALID_REVIEW_STATUS)
        }
        refreshProductRating(review.productId)
        return ReviewResponse.from(review, maskAuthor = false)
    }

    private fun refreshProductRating(productId: Long) {
        val product = productRepository.findById(productId).orElse(null) ?: return
        val row = reviewRepository.aggregateVisibleByProductId(productId).first()
        val avg = (row[0] as Number).toDouble()
        val count = (row[1] as Number).toLong().toInt()
        product.updateReviewSummary(BigDecimal(avg).setScale(1, RoundingMode.HALF_UP), count)
    }
}
