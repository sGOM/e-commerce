package com.example.starter.domain.settlement.repository

import com.example.starter.domain.settlement.entity.SettlementPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementPolicyRepository : JpaRepository<SettlementPolicy, Long> {

    fun findFirstByOrderByIdAsc(): SettlementPolicy?
}
