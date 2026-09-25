package com.example.starter.domain.notification.email

import com.example.starter.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/** 메일로도 나가야 하는 인앱 알림이 저장됐을 때 발행된다. */
data class NotificationEmailRequestedEvent(val userId: Long, val title: String, val body: String)

/**
 * 알림 메일은 커밋 이후 비동기로 보낸다. SMTP 지연이 요청 응답과 DB 트랜잭션을 붙잡지 않고,
 * 알림을 만든 트랜잭션이 롤백되면 메일도 나가지 않는다.
 */
@Component
class NotificationEmailListener(
    private val userRepository: UserRepository,
    private val emailSender: EmailSender,
) {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: NotificationEmailRequestedEvent) = send(event.userId, event.title, event.body)

    /** 메일 발송은 보조 채널 — 실패해도 인앱 알림을 되돌리지 않고 로그만 남긴다. */
    fun send(userId: Long, title: String, body: String) {
        try {
            val email = userRepository.findById(userId).orElse(null)?.email ?: return
            emailSender.send(email, title, body)
        } catch (e: Exception) {
            log.warn("알림 메일 발송 실패 userId={} title={}", userId, title, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(NotificationEmailListener::class.java)
    }
}
