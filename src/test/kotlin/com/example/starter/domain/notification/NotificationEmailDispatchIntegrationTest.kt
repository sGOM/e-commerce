package com.example.starter.domain.notification

import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.notification.email.NotificationEmailListener
import com.example.starter.domain.notification.email.NotificationEmailRequestedEvent
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional

/**
 * 메일 채널 분기 — 어떤 알림이 메일로 나가는지, 발송 실패가 인앱 알림을 깨지 않는지.
 * 실제 발송은 커밋 후 비동기라 롤백되는 테스트에선 일어나지 않으므로, 발행 이벤트와 [NotificationEmailListener.send] 를 나눠 검증한다.
 */
@RecordApplicationEvents
@Transactional
class NotificationEmailDispatchIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var notificationService: NotificationService
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var notificationEmailListener: NotificationEmailListener
    @Autowired lateinit var events: ApplicationEvents

    @MockkBean(relaxed = true)
    lateinit var emailSender: EmailSender

    private fun seedUser(email: String) =
        userRepository.save(User(email = email, password = "{noop}x", name = "회원")).id!!

    @Test
    fun `메일 대상 알림만 메일 발송 이벤트를 발행하고 트랜잭션 안에서는 보내지 않는다`() {
        val userId = seedUser("mail-on-${System.nanoTime()}@example.com")

        notificationService.notify(userId, NotificationType.RESTOCK, "재입고", "상품이 재입고되었습니다.")
        notificationService.notify(userId, NotificationType.LOW_STOCK, "재고 부족", "재고가 얼마 남지 않았습니다.")

        assertThat(events.stream(NotificationEmailRequestedEvent::class.java).toList())
            .containsExactly(NotificationEmailRequestedEvent(userId, "재입고", "상품이 재입고되었습니다."))
        verify(exactly = 0) { emailSender.send(any(), any(), any()) }
    }

    @Test
    fun `메일 발송은 회원 이메일로 보낸다`() {
        val userId = seedUser("mail-send-${System.nanoTime()}@example.com")
        val email = userRepository.findById(userId).get().email

        notificationEmailListener.send(userId, "재입고", "상품이 재입고되었습니다.")

        verify(exactly = 1) { emailSender.send(email, "재입고", "상품이 재입고되었습니다.") }
    }

    @Test
    fun `메일 발송이 실패해도 인앱 알림은 남는다`() {
        val userId = seedUser("mail-fail-${System.nanoTime()}@example.com")
        every { emailSender.send(any(), any(), any()) } throws RuntimeException("SMTP down")

        notificationService.notify(userId, NotificationType.PRICE_DROP, "가격 인하", "찜한 상품의 가격이 내렸습니다.")
        notificationEmailListener.send(userId, "가격 인하", "찜한 상품의 가격이 내렸습니다.") // 예외를 삼킨다

        assertThat(notificationRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, 10)).content).hasSize(1)
    }
}
