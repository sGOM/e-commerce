package com.example.starter.domain.subscription.repository

import com.example.starter.domain.subscription.entity.DeliverySubscription
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional

interface DeliverySubscriptionRepository : JpaRepository<DeliverySubscription, Long>, KotlinJdslJpqlExecutor {

    fun findByUserIdOrderByIdDesc(userId: Long): List<DeliverySubscription>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<DeliverySubscription>

    /** 배치 대상: 활성 구독 중 다음 회차 도래분(조회 시점 판정). */
    fun findByStatusAndNextOrderAtLessThanEqual(status: DeliverySubscriptionStatus, nextOrderAt: Instant): List<DeliverySubscription>
}
