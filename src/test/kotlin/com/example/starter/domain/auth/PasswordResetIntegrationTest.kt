package com.example.starter.domain.auth

import com.example.starter.domain.auth.repository.PasswordResetTokenRepository
import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Instant

/** 비밀번호 재설정(ROADMAP 3.1) — 메일 토큰 30분·1회용, 계정 존재 여부는 응답으로 노출하지 않는다. */
@AutoConfigureMockMvc
class PasswordResetIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var tokenRepository: PasswordResetTokenRepository

    @MockkBean(relaxed = true)
    lateinit var emailSender: EmailSender

    private fun seedUser(): String {
        val email = "reset-${System.nanoTime()}@example.com"
        userRepository.save(User(email = email, password = "{noop}old", name = "회원"))
        return email
    }

    /** 재설정 요청 후 메일 본문에 담긴 토큰을 꺼낸다. */
    private fun requestReset(email: String): String? {
        val body = slot<String>()
        every { emailSender.send(any(), any(), capture(body)) } returns Unit
        mockMvc.post("/api/auth/password-reset/request") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email"}"""
        }.andExpect { status { isOk() } }
        return if (body.isCaptured) Regex("token=([A-Za-z0-9_-]+)").find(body.captured)?.groupValues?.get(1) else null
    }

    private fun confirm(token: String, newPassword: String) = mockMvc.post("/api/auth/password-reset/confirm") {
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"token":"$token","newPassword":"$newPassword"}"""
    }

    private fun login(email: String, password: String) = mockMvc.post("/api/auth/login") {
        with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"email":"$email","password":"$password"}"""
    }

    @Test
    fun `메일 토큰으로 비밀번호를 재설정하면 새 비밀번호로만 로그인된다`() {
        val email = seedUser()

        val token = requestReset(email)!!
        confirm(token, "newpassword1").andExpect { status { isOk() } }

        login(email, "newpassword1").andExpect { status { isOk() } }
        login(email, "old").andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `같은 토큰을 두 번 쓰거나 만료된 토큰은 거절한다`() {
        val email = seedUser()
        val token = requestReset(email)!!
        confirm(token, "newpassword1").andExpect { status { isOk() } }

        confirm(token, "newpassword2").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("AUTH-004") }
        }

        val expiredToken = requestReset(email)!!
        val userId = userRepository.findByEmail(email)!!.id!!
        tokenRepository.saveAll(
            tokenRepository.findByUserIdAndUsedAtIsNull(userId).onEach { it.expiresAt = Instant.now().minusSeconds(1) },
        )
        confirm(expiredToken, "newpassword3").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("AUTH-004") }
        }
        login(email, "newpassword1").andExpect { status { isOk() } }
    }

    @Test
    fun `없는 이메일도 같은 200 을 주고 메일은 보내지 않는다`() {
        val token = requestReset("no-such-${System.nanoTime()}@example.com")

        assertThat(token).isNull()
    }
}
