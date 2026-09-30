package com.example.starter.domain.notification.dto

import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.notification.entity.Notification
import com.example.starter.domain.notification.entity.NotificationType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
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

/** 브라우저 `PushSubscription.toJSON()` 형식 그대로 받는다(웹 푸시 구독, ROADMAP 6.3). */
data class SubscribePushRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val endpoint: String? = null,
    @field:NotNull
    @field:Valid
    val keys: Keys? = null,
) {
    data class Keys(
        @field:NotBlank
        val p256dh: String? = null,
        @field:NotBlank
        val auth: String? = null,
    )
}

/** VAPID 공개키(구독 시 applicationServerKey). 서버에 키가 없으면 null — 프론트는 푸시 토글을 숨긴다. */
data class PushPublicKeyResponse(
    val publicKey: String?,
)

/** 알림 수신 설정 한 줄(종류별 메일·푸시). */
data class NotificationPreferenceResponse(
    val type: NotificationType,
    val email: Boolean,
    val push: Boolean,
)

/** 알림 수신 설정 변경 — 보낸 종류만 바꾼다. */
data class UpdateNotificationPreferenceRequest(
    val type: NotificationType,
    val email: Boolean,
    val push: Boolean,
)
