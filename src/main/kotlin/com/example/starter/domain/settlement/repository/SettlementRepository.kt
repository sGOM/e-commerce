package com.example.starter.domain.settlement.repository

import com.example.starter.domain.settlement.entity.Settlement
import com.example.starter.domain.settlement.entity.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SettlementRepository : JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = ["seller"])
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<Settlement>

    @EntityGraph(attributePaths = ["seller"])
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<Settlement>

    @EntityGraph(attributePaths = ["seller"])
    fun findByStatusOrderByIdDesc(status: SettlementStatus, pageable: Pageable): Page<Settlement>

    @Query("select coalesce(sum(s.payoutAmount), 0L) from Settlement s where s.seller.id = :sellerId and s.status = :status")
    fun sumPayoutAmount(@Param("sellerId") sellerId: Long, @Param("status") status: SettlementStatus): Long
}
