package com.example.starter.security.oauth

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 전용 API 는 `@AuthenticationPrincipal CustomUserDetails` 로 주체를 받는다.
 * 소셜 로그인 주체([CustomOAuth2User])도 같은 방식으로 주입돼야 한다.
 */
@AutoConfigureMockMvc
@Transactional
class OAuthPrincipalIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository

    @Test
    fun `소셜 로그인 사용자도 회원 전용 API 를 사용할 수 있다`() {
        val saved = userRepository.save(User(email = "oauth-api@example.com", password = "{noop}x", name = "소셜"))
        val principal = CustomOAuth2User(saved, mapOf("sub" to "provider-2"), "provider-2")

        mockMvc.get("/api/me/coupons") { with(oauth2Login().oauth2User(principal)) }
            .andExpect { status { isOk() } }
        mockMvc.get("/api/cart") { with(oauth2Login().oauth2User(principal)) }
            .andExpect { status { isOk() } }
    }
}
