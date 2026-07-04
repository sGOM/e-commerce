package com.example.starter.domain.subscription.repository

import com.example.starter.domain.subscription.entity.DeliverySubscriptionHistory
import org.springframework.data.jpa.repository.JpaRepository

interface DeliverySubscriptionHistoryRepository : JpaRepository<DeliverySubscriptionHistory, Long> {

    fun findBySubscriptionIdOrderByIdDesc(subscriptionId: Long): List<DeliverySubscriptionHistory>
}
