package com.example.starter.domain.notification

import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.notification.email.NotificationEmailListener
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.push.NotificationPushListener
import com.example.starter.domain.notification.push.PushResult
import com.example.starter.domain.notification.push.PushSender
import com.example.starter.domain.notification.push.PushSubscription
import com.example.starter.domain.notification.push.PushSubscriptionRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** 알림 수신 설정 — 메일·푸시로 나가는 알림을 종류×채널별로 끈다. 설정이 없으면 받는다(기존 동작 유지). */
@AutoConfigureMockMvc
@Transactional
class NotificationPreferenceIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var pushSubscriptionRepository: PushSubscriptionRepository
    @Autowired lateinit var emailListener: NotificationEmailListener
    @Autowired lateinit var pushListener: NotificationPushListener

    @MockkBean(relaxed = true)
    lateinit var emailSender: EmailSender

    @MockkBean
    lateinit var pushSender: PushSender

    private fun seedMember(email: String): CustomUserDetails {
        val user = userRepository.save(User(email = email, password = "{noop}x", name = "회원"))
        pushSubscriptionRepository.save(
            PushSubscription(userId = user.id!!, endpoint = "https://fcm.googleapis.com/fcm/send/$email", p256dh = "k", auth = "a"),
        )
        every { pushSender.send(any(), any()) } returns PushResult.SENT
        return CustomUserDetails(user)
    }

    private fun event(member: CustomUserDetails, type: NotificationType) =
        NotificationDispatchEvent(member.userId, type, "제목", "본문", null)

    @Test
    fun `설정이 없으면 메일·푸시 대상 알림 종류가 모두 켜진 상태로 보이고 실제로 보낸다`() {
        val member = seedMember("pref-default@example.com")

        mockMvc.get("/api/me/notification-preferences") { with(user(member)) }.andExpect {
            jsonPath("$.data.length()") { value(5) }
            jsonPath("$.data[?(@.type == 'RESTOCK')].email") { value(true) }
            jsonPath("$.data[?(@.type == 'RESTOCK')].push") { value(true) }
            jsonPath("$.data[?(@.type == 'LOW_STOCK')]") { isEmpty() } // 인앱 전용 알림은 설정 대상이 아니다
        }

        emailListener.dispatch(event(member, NotificationType.RESTOCK))
        pushListener.send(event(member, NotificationType.RESTOCK))

        verify(exactly = 1) { emailSender.send(any(), any(), any()) }
        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    @Test
    fun `끈 종류·채널로는 보내지 않고 나머지는 그대로 보낸다`() {
        val member = seedMember("pref-off@example.com")

        mockMvc.put("/api/me/notification-preferences") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """[{"type":"PRICE_DROP","email":false,"push":true},{"type":"RESTOCK","email":true,"push":false}]"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[?(@.type == 'PRICE_DROP')].email") { value(false) }
        }

        emailListener.dispatch(event(member, NotificationType.PRICE_DROP)) // 메일 끔
        pushListener.send(event(member, NotificationType.PRICE_DROP)) // 푸시 켬
        emailListener.dispatch(event(member, NotificationType.RESTOCK)) // 메일 켬
        pushListener.send(event(member, NotificationType.RESTOCK)) // 푸시 끔

        verify(exactly = 1) { emailSender.send(any(), "제목", any()) }
        verify(exactly = 1) { pushSender.send(any(), any()) }
    }

    @Test
    fun `메일·푸시 대상이 아닌 종류는 설정할 수 없다`() {
        val member = seedMember("pref-invalid@example.com")

        mockMvc.put("/api/me/notification-preferences") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """[{"type":"LOW_STOCK","email":false,"push":false}]"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("NOTIFICATION-002") }
        }
    }
}
