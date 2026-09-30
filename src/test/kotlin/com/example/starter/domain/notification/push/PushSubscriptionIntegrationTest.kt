package com.example.starter.domain.notification.push

import com.example.starter.domain.notification.NotificationDispatchEvent
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

@AutoConfigureMockMvc
@Transactional
class PushSubscriptionIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var pushSubscriptionRepository: PushSubscriptionRepository
    @Autowired lateinit var listener: NotificationPushListener

    @MockkBean
    lateinit var pushSender: PushSender

    companion object {
        private val b64 = Base64.getUrlEncoder().withoutPadding()
        private val vapid = WebPushCrypto.generateKeyPair()
        val vapidPublic: String = b64.encodeToString(WebPushCrypto.rawPublic(vapid.public))

        @JvmStatic
        @DynamicPropertySource
        fun vapidKeys(registry: DynamicPropertyRegistry) {
            registry.add("push.vapid.public-key") { vapidPublic }
            registry.add("push.vapid.private-key") { "dGVzdA" } // 발송기는 목으로 대체되므로 값 형식만 채운다
        }
    }

    private val browserKey = b64.encodeToString(WebPushCrypto.rawPublic(WebPushCrypto.generateKeyPair().public))
    private val authSecret = b64.encodeToString(ByteArray(16) { it.toByte() })

    private fun seedUser(email: String) =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun subscribe(user: CustomUserDetails, endpoint: String, p256dh: String = browserKey) =
        mockMvc.post("/api/me/push-subscriptions") {
            with(user(user)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"endpoint":"$endpoint","keys":{"p256dh":"$p256dh","auth":"$authSecret"}}"""
        }

    @Test
    fun `구독에 쓸 VAPID 공개키를 내려준다`() {
        val member = seedUser("push-key@example.com")
        mockMvc.get("/api/me/push-subscriptions/public-key") { with(user(member)) }
            .andExpect { jsonPath("$.data.publicKey") { value(vapidPublic) } }
    }

    @Test
    fun `같은 브라우저를 다른 계정이 구독하면 구독이 새 계정으로 옮겨간다`() {
        val first = seedUser("push-first@example.com")
        val second = seedUser("push-second@example.com")
        val endpoint = "https://fcm.googleapis.com/fcm/send/same-browser"

        subscribe(first, endpoint).andExpect { status { isOk() } }
        subscribe(second, endpoint).andExpect { status { isOk() } }

        assertThat(pushSubscriptionRepository.findByEndpoint(endpoint)!!.userId).isEqualTo(second.userId)
        assertThat(pushSubscriptionRepository.findByUserId(first.userId)).isEmpty()
    }

    @Test
    fun `알려진 푸시 서비스가 아닌 주소나 깨진 키는 받지 않는다`() {
        val member = seedUser("push-invalid@example.com")

        listOf(
            "http://fcm.googleapis.com/fcm/send/x", // https 아님
            "https://169.254.169.254/latest/meta-data", // 내부망(SSRF)
            "https://fcm.googleapis.com.evil.com/x", // 접미사 위장
        ).forEach { endpoint ->
            subscribe(member, endpoint).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("PUSH-001") }
            }
        }
        subscribe(member, "https://updates.push.services.mozilla.com/wpush/v2/x", p256dh = "AAAA")
            .andExpect { jsonPath("$.code") { value("PUSH-001") } }
    }

    @Test
    fun `알림은 회원의 모든 구독으로 보내고 만료된 구독은 지운다`() {
        val member = seedUser("push-send@example.com")
        subscribe(member, "https://fcm.googleapis.com/fcm/send/alive").andExpect { status { isOk() } }
        subscribe(member, "https://web.push.apple.com/gone").andExpect { status { isOk() } }
        subscribe(member, "https://db5p.notify.windows.com/w/?token=broken").andExpect { status { isOk() } }
        val payload = slot<String>()
        every { pushSender.send(match { it.endpoint.endsWith("alive") }, capture(payload)) } returns PushResult.SENT
        every { pushSender.send(match { it.endpoint.endsWith("gone") }, any()) } returns PushResult.GONE
        every { pushSender.send(match { it.endpoint.contains("broken") }, any()) } throws RuntimeException("timeout")

        listener.send(NotificationDispatchEvent(member.userId, "재입고", "상품이 재입고되었습니다.", "/products/1"))

        verify(exactly = 3) { pushSender.send(any(), any()) }
        assertThat(payload.captured).contains("\"title\":\"재입고\"", "\"url\":\"/products/1\"")
        assertThat(pushSubscriptionRepository.findByUserId(member.userId).map { it.endpoint })
            .containsExactlyInAnyOrder("https://fcm.googleapis.com/fcm/send/alive", "https://db5p.notify.windows.com/w/?token=broken")
    }
}
