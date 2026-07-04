package com.example.starter.domain.review.repository

import com.example.starter.domain.review.entity.ReviewRewardPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface ReviewRewardPolicyRepository : JpaRepository<ReviewRewardPolicy, Long> {

    /** 단일 정책 행을 가져온다(마이그레이션에서 기본 1행 시드). */
    fun findFirstByOrderByIdAsc(): ReviewRewardPolicy?
}
