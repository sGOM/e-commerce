package com.example.starter.domain.review

import com.example.starter.domain.review.dto.ReviewRewardPolicyResponse
import com.example.starter.domain.review.dto.UpdateReviewRewardPolicyRequest
import com.example.starter.domain.review.entity.ReviewRewardPolicy
import com.example.starter.domain.review.repository.ReviewRewardPolicyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 리뷰 정책 관리(관리자). 적립 포인트/작성 가능 기간/신고 임계치를 단일 정책 행으로 런타임 변경한다.
 */
@Service
@Transactional(readOnly = true)
class ReviewRewardPolicyService(
    private val reviewRewardPolicyRepository: ReviewRewardPolicyRepository,
) {

    fun getPolicy(): ReviewRewardPolicyResponse = currentPolicy().toResponse()

    /** 전달한 항목만 변경한다(부분 업데이트). */
    @Transactional
    fun update(request: UpdateReviewRewardPolicyRequest): ReviewRewardPolicyResponse {
        val policy = currentPolicy()
        request.textReviewPoint?.let { policy.textReviewPoint = it }
        request.photoReviewPoint?.let { policy.photoReviewPoint = it }
        request.reviewableDays?.let { policy.reviewableDays = it }
        request.reportThreshold?.let { policy.reportThreshold = it }
        return policy.toResponse()
    }

    private fun ReviewRewardPolicy.toResponse() = ReviewRewardPolicyResponse(
        textReviewPoint = textReviewPoint,
        photoReviewPoint = photoReviewPoint,
        reviewableDays = reviewableDays,
        reportThreshold = reportThreshold,
    )

    /** 마이그레이션이 1행을 시드하지만, 부재 시 기본값으로 생성해 NPE 를 막는다. */
    private fun currentPolicy(): ReviewRewardPolicy =
        reviewRewardPolicyRepository.findFirstByOrderByIdAsc()
            ?: reviewRewardPolicyRepository.save(
                ReviewRewardPolicy(textReviewPoint = 0, photoReviewPoint = 0, reviewableDays = 90, reportThreshold = 5),
            )
}
