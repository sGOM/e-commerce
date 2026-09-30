package com.example.starter.domain.notification.push

import com.example.starter.domain.notification.NotificationDispatchEvent
import com.example.starter.domain.notification.NotificationPreferenceService
import com.example.starter.domain.notification.entity.NotificationChannel
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 알림을 회원의 모든 브라우저 구독으로 푸시한다. 메일과 같게 커밋 후 비동기이며 실패해도 인앱 알림은 남는다.
 * 만료된 구독(404/410)은 여기서 지운다 — 브라우저에서 구독을 끄면 다음 발송 때 정리된다.
 */
@Component
class NotificationPushListener(
    private val pushSubscriptionRepository: PushSubscriptionRepository,
    private val pushSender: PushSender,
    private val objectMapper: ObjectMapper,
    private val preferenceService: NotificationPreferenceService,
) {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: NotificationDispatchEvent) = send(event)

    fun send(event: NotificationDispatchEvent) {
        if (!preferenceService.isEnabled(event.userId, event.type, NotificationChannel.PUSH)) return
        // 서비스워커(public/sw.js)가 읽는 형식. 레코드 한도(4KB) 안에 들도록 본문을 자른다.
        val payload = objectMapper.writeValueAsString(
            mapOf("title" to event.title, "body" to event.body.take(MAX_BODY), "url" to (event.linkUrl ?: "/notifications")),
        )
        pushSubscriptionRepository.findByUserId(event.userId).forEach { subscription ->
            try {
                if (pushSender.send(subscription, payload) == PushResult.GONE) {
                    pushSubscriptionRepository.delete(subscription)
                }
            } catch (e: Exception) {
                log.warn("웹 푸시 발송 실패 userId={} subscriptionId={}", event.userId, subscription.id, e)
            }
        }
    }

    companion object {
        private const val MAX_BODY = 1_000
        private val log = LoggerFactory.getLogger(NotificationPushListener::class.java)
    }
}
