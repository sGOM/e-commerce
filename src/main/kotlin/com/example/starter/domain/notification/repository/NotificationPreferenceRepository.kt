package com.example.starter.domain.notification.repository

import com.example.starter.domain.notification.entity.NotificationChannel
import com.example.starter.domain.notification.entity.NotificationPreference
import com.example.starter.domain.notification.entity.NotificationType
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, Long> {
    fun findByUserId(userId: Long): List<NotificationPreference>
    fun findByUserIdAndTypeAndChannel(userId: Long, type: NotificationType, channel: NotificationChannel): NotificationPreference?
}
