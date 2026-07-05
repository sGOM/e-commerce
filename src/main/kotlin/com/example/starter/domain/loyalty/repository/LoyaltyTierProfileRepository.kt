package com.example.starter.domain.loyalty.repository

import com.example.starter.domain.loyalty.entity.LoyaltyTier
import com.example.starter.domain.loyalty.entity.LoyaltyTierProfile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface LoyaltyTierProfileRepository : JpaRepository<LoyaltyTierProfile, Long> {

    fun findByUserId(userId: Long): Optional<LoyaltyTierProfile>

    fun findByTier(tier: LoyaltyTier, pageable: Pageable): Page<LoyaltyTierProfile>

    /**
     * 이미 등급 프로필이 있는 모든 회원 id. 배치 재계산 대상 산정에 쓰인다 — 최근 12개월 순구매
     * 실적이 없어졌어도(윈도우 이탈) 강등 재평가가 필요하므로, 구매 실적 집계 결과와 합집합한다
     * ([com.example.starter.domain.loyalty.LoyaltyTierBatchService.recalculateAll]).
     */
    @Query("SELECT p.userId FROM LoyaltyTierProfile p")
    fun findAllUserIds(): List<Long>
}
