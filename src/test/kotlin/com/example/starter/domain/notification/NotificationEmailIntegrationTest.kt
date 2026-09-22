package com.example.starter.domain.notification

import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.notification.email.LoggingEmailSender
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

/**
 * SMTP 설정(`spring.mail.host`)이 없는 환경 — 발송기는 로깅 구현으로 떨어지고 인앱 알림은 그대로 동작해야 한다.
 */
@Transactional
class NotificationEmailIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var notificationService: NotificationService
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var emailSender: EmailSender

    @Test
    fun `SMTP 설정이 없으면 로깅 발송기를 쓰고 인앱 알림은 그대로 저장된다`() {
        val user = userRepository.save(User(email = "mail-off@example.com", password = "{noop}x", name = "회원"))

        notificationService.notify(user.id!!, NotificationType.RESTOCK, "재입고", "상품이 재입고되었습니다.")

        assertThat(emailSender).isInstanceOf(LoggingEmailSender::class.java)
        assertThat(notificationRepository.findByUserIdOrderByIdDesc(user.id!!, PageRequest.of(0, 10)).content).hasSize(1)
    }
}
