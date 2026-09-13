package com.example.starter.domain.notification

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.notification.dto.MyNotificationsResponse
import com.example.starter.domain.notification.dto.NotificationResponse
import com.example.starter.domain.notification.entity.Notification
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 범용 인앱 알림함. 재입고 외 다른 도메인(주문상태변경/쿠폰 등)도 [notify] 를 그대로 호출해
 * 재사용할 수 있다. 이메일/푸시 채널은 인프라가 없어 아직 없음(README 공통 오픈 이슈) — 채널을
 * 추가할 때는 이 서비스에 채널별 발송기를 주입하는 방식으로 확장하고, 호출측(`notify`)은 그대로 둔다.
 */
@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {

    /** 인앱 알림 생성. 실패해도 호출측(재고 갱신 등) 트랜잭션에 영향을 주지 않도록 항상 별도 호출로 사용한다. */
    @Transactional
    fun notify(userId: Long, type: NotificationType, title: String, body: String, linkUrl: String? = null) {
        notificationRepository.save(
            Notification(userId = userId, type = type, title = title, body = body, linkUrl = linkUrl),
        )
    }

    fun getMyNotifications(userId: Long, unreadOnly: Boolean, pageable: Pageable): MyNotificationsResponse {
        val page = if (unreadOnly) {
            notificationRepository.findByUserIdAndIsReadFalseOrderByIdDesc(userId, pageable)
        } else {
            notificationRepository.findByUserIdOrderByIdDesc(userId, pageable)
        }
        val unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId)
        return MyNotificationsResponse(
            notifications = PageResponse.of(page) { NotificationResponse.from(it) },
            unreadCount = unreadCount,
        )
    }

    /** 본인 알림 읽음 처리(AC8). 남의 알림은 404 로 존재를 숨긴다. */
    @Transactional
    fun markRead(userId: Long, notificationId: Long) {
        val notification = notificationRepository.findByIdAndUserId(notificationId, userId)
            .orElseThrow { BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND) }
        notification.markRead()
    }
}
