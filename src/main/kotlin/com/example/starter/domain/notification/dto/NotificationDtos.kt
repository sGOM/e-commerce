package com.example.starter.domain.notification.dto

import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.notification.entity.Notification
import com.example.starter.domain.notification.entity.NotificationType
import java.time.Instant

/** 알림 1건 응답. */
data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val linkUrl: String?,
    val isRead: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = requireNotNull(notification.id),
            type = notification.type,
            title = notification.title,
            body = notification.body,
            linkUrl = notification.linkUrl,
            isRead = notification.isRead,
            createdAt = notification.createdAt,
        )
    }
}

/** 내 알림함 응답 — 목록과 함께 미확인 개수 배지(AC8)를 한 번에 내려준다. */
data class MyNotificationsResponse(
    val notifications: PageResponse<NotificationResponse>,
    val unreadCount: Long,
)
