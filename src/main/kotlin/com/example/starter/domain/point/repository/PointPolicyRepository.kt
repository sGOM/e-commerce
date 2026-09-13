package com.example.starter.domain.point.repository

import com.example.starter.domain.point.entity.PointPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface PointPolicyRepository : JpaRepository<PointPolicy, Long> {

    /** 단일 정책 행을 가져온다(마이그레이션에서 기본 1행 시드). */
    fun findFirstByOrderByIdAsc(): PointPolicy?
}
