package com.example.starter.domain.settlement.repository

import com.example.starter.domain.settlement.entity.Settlement
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementRepository : JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = ["seller"])
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<Settlement>
}
