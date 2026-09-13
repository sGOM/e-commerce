package com.example.starter.domain.review.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 리뷰 관련 정책. 단일 행을 유지하며 관리자가 런타임 변경한다(배포 불필요 — 다른 정책 테이블과 동일 원칙).
 *
 * - [textReviewPoint]/[photoReviewPoint]: 텍스트/포토 리뷰 작성 적립 포인트(원).
 * - [reviewableDays]: 배송완료(구매확정) 후 리뷰 작성 가능 기간(일).
 * - [reportThreshold]: 이 값 이상 신고 누적 시 [ReviewStatus.REPORTED] 로 자동 전이.
 */
@Entity
@Table(name = "review_reward_policies")
class ReviewRewardPolicy(
    @Column(name = "text_review_point", nullable = false)
    var textReviewPoint: Long,

    @Column(name = "photo_review_point", nullable = false)
    var photoReviewPoint: Long,

    @Column(name = "reviewable_days", nullable = false)
    var reviewableDays: Int = 90,

    @Column(name = "report_threshold", nullable = false)
    var reportThreshold: Int = 5,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
