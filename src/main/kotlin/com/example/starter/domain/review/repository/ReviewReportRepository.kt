package com.example.starter.domain.review.repository

import com.example.starter.domain.review.entity.ReviewReport
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewReportRepository : JpaRepository<ReviewReport, Long> {

    fun existsByReviewIdAndReporterId(reviewId: Long, reporterId: Long): Boolean
}
