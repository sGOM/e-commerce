package com.example.starter.domain.gift.repository

import com.example.starter.domain.gift.entity.GiftPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface GiftPolicyRepository : JpaRepository<GiftPolicy, Long> {

    fun findFirstByOrderByIdAsc(): GiftPolicy?
}
