package com.example.starter.domain.notification

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.notification.dto.NotificationPreferenceResponse
import com.example.starter.domain.notification.dto.UpdateNotificationPreferenceRequest
import com.example.starter.domain.notification.entity.NotificationChannel
import com.example.starter.domain.notification.entity.NotificationPreference
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 수신 설정. 메일·푸시로 나가는 종류([NotificationService.OUT_OF_APP_TYPES])만 대상이며, 행이 없으면 받는다
 * (설정 도입 전 동작 유지). 각 채널 리스너가 발송 직전에 [isEnabled] 로 확인한다.
 */
@Service
@Transactional(readOnly = true)
class NotificationPreferenceService(
    private val repository: NotificationPreferenceRepository,
) {

    fun get(userId: Long): List<NotificationPreferenceResponse> {
        val off = repository.findByUserId(userId).filterNot { it.enabled }.map { it.type to it.channel }.toSet()
        return NotificationService.OUT_OF_APP_TYPES.map { type ->
            NotificationPreferenceResponse(
                type = type,
                email = (type to NotificationChannel.EMAIL) !in off,
                push = (type to NotificationChannel.PUSH) !in off,
            )
        }
    }

    fun isEnabled(userId: Long, type: NotificationType, channel: NotificationChannel): Boolean =
        repository.findByUserIdAndTypeAndChannel(userId, type, channel)?.enabled ?: true

    @Transactional
    fun update(userId: Long, requests: List<UpdateNotificationPreferenceRequest>): List<NotificationPreferenceResponse> {
        if (requests.any { it.type !in NotificationService.OUT_OF_APP_TYPES }) {
            throw BusinessException(ErrorCode.NOTIFICATION_PREFERENCE_NOT_SUPPORTED)
        }
        requests.forEach { request ->
            set(userId, request.type, NotificationChannel.EMAIL, request.email)
            set(userId, request.type, NotificationChannel.PUSH, request.push)
        }
        return get(userId)
    }

    private fun set(userId: Long, type: NotificationType, channel: NotificationChannel, enabled: Boolean) {
        val existing = repository.findByUserIdAndTypeAndChannel(userId, type, channel)
        if (existing != null) {
            existing.enabled = enabled
        } else {
            repository.save(NotificationPreference(userId = userId, type = type, channel = channel, enabled = enabled))
        }
    }
}
