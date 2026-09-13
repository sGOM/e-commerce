package com.example.starter.domain.review

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.OrderItemRepository
import com.example.starter.domain.point.PointService
import com.example.starter.domain.review.dto.CreateReviewRequest
import com.example.starter.domain.review.dto.ReportReviewRequest
import com.example.starter.domain.review.dto.ReviewResponse
import com.example.starter.domain.review.dto.ReviewSearchCondition
import com.example.starter.domain.review.dto.ReviewSort
import com.example.starter.domain.review.dto.ReviewableOrderItemResponse
import com.example.starter.domain.review.dto.UpdateReviewRequest
import com.example.starter.domain.review.entity.Review
import com.example.starter.domain.review.entity.ReviewReport
import com.example.starter.domain.review.entity.ReviewRewardPolicy
import com.example.starter.domain.review.entity.ReviewStatus
import com.example.starter.domain.review.repository.ReviewReportRepository
import com.example.starter.domain.review.repository.ReviewRepository
import com.example.starter.domain.review.repository.ReviewRewardPolicyRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 상품 리뷰. 구매 인증은 기존 `OrderItem`/`SubOrder.status`(DELIVERED)를 그대로 활용하며,
 * 신규 상태 전이는 만들지 않는다(읽기 전용 참조). 리뷰 변경 시점마다 [ProductRepository] 를 통해
 * 상품의 평점 요약(비정규화)을 재계산한다.
 */
@Service
@Transactional(readOnly = true)
class ReviewService(
    private val reviewRepository: ReviewRepository,
    private val reviewReportRepository: ReviewReportRepository,
    private val reviewRewardPolicyRepository: ReviewRewardPolicyRepository,
    private val orderItemRepository: OrderItemRepository,
    private val productRepository: ProductRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val userRepository: UserRepository,
    private val pointService: PointService,
) {

    /** 구매 인증 → 배송완료·기간 검증 → 저장 → (최초 1회) 포인트 적립 → 상품 평점 재계산. */
    @Transactional
    fun create(userId: Long, request: CreateReviewRequest): ReviewResponse {
        val orderItem = orderItemRepository.findWithSubOrderById(request.orderItemId!!)
            .orElseThrow { BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND) }
        val subOrder = orderItem.subOrder
        if (subOrder.order.userId != userId) {
            // 본인이 구매하지 않은 항목/타인의 주문 — 403 이 아닌 404 로 존재를 숨긴다(cancelSubOrder 컨벤션, AC4)
            throw BusinessException(ErrorCode.ORDER_ITEM_NOT_FOUND)
        }
        if (subOrder.status != SubOrderStatus.DELIVERED) {
            throw BusinessException(ErrorCode.REVIEW_NOT_ALLOWED)
        }
        val policy = currentPolicy()
        subOrder.deliveredAt?.let { deliveredAt ->
            if (Instant.now().isAfter(deliveredAt.plus(policy.reviewableDays.toLong(), ChronoUnit.DAYS))) {
                throw BusinessException(ErrorCode.REVIEW_PERIOD_EXPIRED)
            }
        }
        val orderItemId = requireNotNull(orderItem.id)
        if (reviewRepository.existsByOrderItemId(orderItemId)) {
            throw BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS)
        }

        val user = userRepository.findById(userId).orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        val product = productOptionRepository.findWithProductAndInventoryById(orderItem.optionId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }
            .product

        val review = Review(
            orderItemId = orderItemId,
            productId = requireNotNull(product.id),
            userId = userId,
            authorName = user.name,
            rating = request.rating!!,
            content = request.content!!,
        )
        review.replaceImages(request.imageUrls)
        reviewRepository.save(review)

        // 어뷰징(삭제 후 재작성 반복) 방지 — OrderItem 당 적립은 최초 1회만(AC16). 삭제해도 회수하지 않는다(AC7).
        if (!orderItem.reviewRewarded) {
            val amount = if (review.hasPhoto) policy.photoReviewPoint else policy.textReviewPoint
            if (amount > 0) pointService.earnForReview(userId, amount)
            orderItem.markReviewRewarded()
        }

        refreshProductRating(review.productId)
        return ReviewResponse.from(review, maskAuthor = false)
    }

    /** 본인 리뷰 수정(AC6). */
    @Transactional
    fun update(userId: Long, reviewId: Long, request: UpdateReviewRequest): ReviewResponse {
        val review = findOwnedReview(userId, reviewId)
        review.update(request.rating!!, request.content!!, request.imageUrls)
        refreshProductRating(review.productId)
        return ReviewResponse.from(review, maskAuthor = false)
    }

    /** 본인 리뷰 삭제(AC6). 이미 지급된 적립 포인트는 회수하지 않는다(AC7, OrderItem.reviewRewarded 로 별도 관리). */
    @Transactional
    fun delete(userId: Long, reviewId: Long) {
        val review = findOwnedReview(userId, reviewId)
        val productId = review.productId
        reviewRepository.delete(review)
        refreshProductRating(productId)
    }

    /** 리뷰 신고(AC11). 동일 리뷰 중복 신고는 조용히 무시해 1회로 집계한다. */
    @Transactional
    fun report(userId: Long, reviewId: Long, request: ReportReviewRequest) {
        val review = reviewRepository.findById(reviewId)
            .orElseThrow { BusinessException(ErrorCode.REVIEW_NOT_FOUND) }
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, userId)) return
        reviewReportRepository.save(ReviewReport(reviewId = reviewId, reporterId = userId, reason = request.reason!!))
        review.report(currentPolicy().reportThreshold)
    }

    /** 상품 리뷰 목록(공개) — HIDDEN 제외, 정렬/포토 필터(AC8~AC10). */
    fun listByProduct(productId: Long, condition: ReviewSearchCondition, pageable: Pageable): PageResponse<ReviewResponse> {
        val page = reviewRepository.findPage(pageable) {
            val query = select(entity(Review::class))
                .from(entity(Review::class))
                .whereAnd(
                    path(Review::productId).eq(productId),
                    path(Review::status).ne(ReviewStatus.HIDDEN),
                    if (condition.photoOnly) path(Review::hasPhoto).eq(true) else null,
                )
            when (condition.sort) {
                ReviewSort.RATING_DESC -> query.orderBy(path(Review::rating).desc(), path(Review::id).desc())
                ReviewSort.LATEST -> query.orderBy(path(Review::id).desc())
            }
        }
        return PageResponse.of(page) { ReviewResponse.from(it!!, maskAuthor = true) }
    }

    /** 내가 쓴 리뷰 목록 — HIDDEN 포함(작성자 본인에게는 숨김 안내와 함께 노출, AC14). */
    fun getMyReviews(userId: Long, pageable: Pageable): PageResponse<ReviewResponse> =
        PageResponse.of(reviewRepository.findByUserIdOrderByIdDesc(userId, pageable)) {
            ReviewResponse.from(it, maskAuthor = false)
        }

    /** 리뷰 작성 가능한 주문 항목 목록(마이페이지) — 배송완료 + 미작성 + 기간 이내. */
    fun getReviewableOrderItems(userId: Long): List<ReviewableOrderItemResponse> {
        val policy = currentPolicy()
        val cutoff = Instant.now().minus(policy.reviewableDays.toLong(), ChronoUnit.DAYS)
        val candidates = orderItemRepository
            .findBySubOrder_Order_UserIdAndSubOrder_StatusOrderByIdDesc(userId, SubOrderStatus.DELIVERED)
            .filter { !reviewRepository.existsByOrderItemId(requireNotNull(it.id)) }
            .filter { item -> item.subOrder.deliveredAt?.isAfter(cutoff) ?: true }
        if (candidates.isEmpty()) return emptyList()

        val optionsById = productOptionRepository
            .findWithProductByIdIn(candidates.map { it.optionId }.distinct())
            .associateBy { it.id }
        return candidates.map { item ->
            val product = optionsById[item.optionId]?.product
            ReviewableOrderItemResponse(
                orderItemId = requireNotNull(item.id),
                subOrderId = requireNotNull(item.subOrder.id),
                productId = requireNotNull(product?.id),
                productName = item.productName,
                optionName = item.optionName,
                deliveredAt = item.subOrder.deliveredAt,
            )
        }
    }

    /** 상품 평점 요약(비정규화) 재계산 — HIDDEN 제외 집계 결과로 갱신한다. */
    private fun refreshProductRating(productId: Long) {
        val product = productRepository.findById(productId).orElse(null) ?: return
        val row = reviewRepository.aggregateVisibleByProductId(productId).first()
        val avg = (row[0] as Number).toDouble()
        val count = (row[1] as Number).toLong().toInt()
        val rounded = BigDecimal(avg).setScale(1, RoundingMode.HALF_UP)
        product.updateReviewSummary(rounded, count)
    }

    private fun findOwnedReview(userId: Long, reviewId: Long): Review =
        reviewRepository.findByIdAndUserId(reviewId, userId)
            .orElseThrow { BusinessException(ErrorCode.REVIEW_NOT_FOUND) }

    /** 마이그레이션이 1행을 시드하지만, 부재 시 기본값으로 생성해 NPE 를 막는다. */
    private fun currentPolicy(): ReviewRewardPolicy =
        reviewRewardPolicyRepository.findFirstByOrderByIdAsc()
            ?: reviewRewardPolicyRepository.save(
                ReviewRewardPolicy(textReviewPoint = 0, photoReviewPoint = 0, reviewableDays = 90, reportThreshold = 5),
            )
}
