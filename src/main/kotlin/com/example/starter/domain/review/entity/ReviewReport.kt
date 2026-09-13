package com.example.starter.domain.review.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 리뷰 신고. `unique(reviewId, reporterId)` 로 동일 리뷰 중복 신고를 1회로 집계한다(AC11).
 * [Review] 애그리거트 밖에서 독립적으로 쌓이는 로그성 데이터라 연관관계 대신 순수 id 참조를 쓴다.
 */
@Entity
@Table(name = "review_reports")
class ReviewReport(
    @Column(name = "review_id", nullable = false)
    val reviewId: Long,

    @Column(name = "reporter_id", nullable = false)
    val reporterId: Long,

    @Column(nullable = false, length = 500)
    val reason: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
