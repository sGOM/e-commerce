package com.example.starter.domain.delivery.repository

import com.example.starter.domain.delivery.entity.ShippingPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface ShippingPolicyRepository : JpaRepository<ShippingPolicy, Long> {
    fun findFirstByOrderByIdAsc(): ShippingPolicy?
}
