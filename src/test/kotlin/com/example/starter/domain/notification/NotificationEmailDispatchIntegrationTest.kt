package com.example.starter.domain.notification

import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional

/** 메일 채널 분기 — 어떤 알림이 메일로 나가는지, 발송 실패가 인앱 알림을 깨지 않는지. */
@Transactional
class NotificationEmailDispatchIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var notificationService: NotificationService
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var userRepository: UserRepository

    @MockkBean(relaxed = true) lateinit var emailSender: EmailSender

    private fun seedUser(email: String) =
        userRepository.save(User(email = email, password = "{noop}x", name = "회원")).id!!

    @Test
    fun `메일 대상 알림은 회원 이메일로 발송하고 그 외 알림은 발송하지 않는다`() {
        val userId = seedUser("mail-on-${System.nanoTime()}@example.com")
        val email = userRepository.findById(userId).get().email

        notificationService.notify(userId, NotificationType.RESTOCK, "재입고", "상품이 재입고되었습니다.")
        notificationService.notify(userId, NotificationType.LOW_STOCK, "재고 부족", "재고가 얼마 남지 않았습니다.")

        verify(exactly = 1) { emailSender.send(email, "재입고", "상품이 재입고되었습니다.") }
        verify(exactly = 0) { emailSender.send(any(), "재고 부족", any()) }
    }

    @Test
    fun `메일 발송이 실패해도 인앱 알림은 남는다`() {
        val userId = seedUser("mail-fail-${System.nanoTime()}@example.com")
        every { emailSender.send(any(), any(), any()) } throws RuntimeException("SMTP down")

        notificationService.notify(userId, NotificationType.PRICE_DROP, "가격 인하", "찜한 상품의 가격이 내렸습니다.")

        assertThat(notificationRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, 10)).content).hasSize(1)
    }
}
