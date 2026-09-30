package com.example.starter.domain.notification.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 인앱 밖 알림 채널. */
enum class NotificationChannel { EMAIL, PUSH }

/** 회원의 알림 수신 설정 한 칸(종류×채널). 행이 없으면 켜진 것으로 본다. */
@Entity
@Table(name = "notification_preferences")
class NotificationPreference(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val type: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    val channel: NotificationChannel,

    @Column(nullable = false)
    var enabled: Boolean,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
