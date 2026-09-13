package com.example.starter.domain.gift.repository

import com.example.starter.domain.gift.entity.GiftClaim
import com.example.starter.domain.gift.entity.GiftClaimStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional

interface GiftClaimRepository : JpaRepository<GiftClaim, Long> {

    fun findByToken(token: String): Optional<GiftClaim>

    fun findByOrderId(orderId: Long): Optional<GiftClaim>

    /** 관리자 모니터링(상태별 필터). */
    fun findByStatusOrderByIdDesc(status: GiftClaimStatus, pageable: Pageable): Page<GiftClaim>

    fun findAllByOrderByIdDesc(pageable: Pageable): Page<GiftClaim>

    /** 미수락 만료 배치 대상(AC9) — PENDING 이면서 만료기한이 지난 링크. */
    fun findByStatusAndExpiresAtBefore(status: GiftClaimStatus, expiresAt: Instant): List<GiftClaim>
}
