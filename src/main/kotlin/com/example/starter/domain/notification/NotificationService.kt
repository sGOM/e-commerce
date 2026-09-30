package com.example.starter.domain.notification

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.notification.dto.MyNotificationsResponse
import com.example.starter.domain.notification.dto.NotificationResponse
import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.notification.entity.Notification
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 범용 인앱 알림함. 재입고 외 다른 도메인(주문상태변경/쿠폰 등)도 [notify] 를 그대로 호출해
 * 재사용할 수 있다. 인앱과 함께 **메일**([EmailSender], ROADMAP 6.1)·**웹 푸시**(ROADMAP 6.3) 채널로도 커밋 후 비동기로 나가며,
 * 대상은 [OUT_OF_APP_TYPES] 로 한정한다 — 저재고·장바구니 리마인드처럼 자주 뜨는 알림까지 밖으로 보내지 않기 위함이다.
 * 회원은 종류×채널별로 메일·푸시를 끌 수 있다([NotificationPreferenceService] — 각 채널 리스너가 발송 직전 확인).
 */
@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    /** 인앱 알림 생성. 실패해도 호출측(재고 갱신 등) 트랜잭션에 영향을 주지 않도록 항상 별도 호출로 사용한다. */
    @Transactional
    fun notify(userId: Long, type: NotificationType, title: String, body: String, linkUrl: String? = null) {
        notificationRepository.save(
            Notification(userId = userId, type = type, title = title, body = body, linkUrl = linkUrl),
        )
        if (type in OUT_OF_APP_TYPES) {
            eventPublisher.publishEvent(NotificationDispatchEvent(userId, type, title, body, linkUrl))
        }
    }

    companion object {
        /** 메일·푸시로도 보내는 알림 — 사용자가 기다리는 1회성 소식만 넣는다. 수신 설정 대상이기도 하다. */
        val OUT_OF_APP_TYPES = setOf(
            NotificationType.RESTOCK,
            NotificationType.PRICE_DROP,
            NotificationType.MEMBERSHIP,
            NotificationType.DELIVERY_SUBSCRIPTION,
            NotificationType.GIFT,
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
