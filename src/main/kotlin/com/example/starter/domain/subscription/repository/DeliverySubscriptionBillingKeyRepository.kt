package com.example.starter.domain.subscription.repository

import com.example.starter.domain.subscription.entity.DeliverySubscriptionBillingKey
import org.springframework.data.jpa.repository.JpaRepository

interface DeliverySubscriptionBillingKeyRepository : JpaRepository<DeliverySubscriptionBillingKey, Long> {

    fun findByUserId(userId: Long): DeliverySubscriptionBillingKey?
}
