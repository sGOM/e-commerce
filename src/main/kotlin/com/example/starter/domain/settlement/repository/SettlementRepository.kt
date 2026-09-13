package com.example.starter.domain.settlement.repository

import com.example.starter.domain.settlement.entity.Settlement
import com.example.starter.domain.settlement.entity.SettlementStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = ["seller"])
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<Settlement>

    @EntityGraph(attributePaths = ["seller"])
    fun findAllByOrderByIdDesc(pageable: Pageable): Page<Settlement>

    @EntityGraph(attributePaths = ["seller"])
    fun findByStatusOrderByIdDesc(status: SettlementStatus, pageable: Pageable): Page<Settlement>
}
