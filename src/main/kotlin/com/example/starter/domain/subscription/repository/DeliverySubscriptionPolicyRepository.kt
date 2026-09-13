package com.example.starter.domain.subscription.repository

import com.example.starter.domain.subscription.entity.DeliverySubscriptionPolicy
import org.springframework.data.jpa.repository.JpaRepository

interface DeliverySubscriptionPolicyRepository : JpaRepository<DeliverySubscriptionPolicy, Long> {

    fun findFirstByOrderByIdAsc(): DeliverySubscriptionPolicy?
}
