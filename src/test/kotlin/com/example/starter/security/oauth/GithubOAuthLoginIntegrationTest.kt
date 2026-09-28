package com.example.starter.security.oauth

import com.example.starter.support.AbstractIntegrationTest
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/** `oauth-github` 프로파일만 켜도 GitHub 로그인이 동작하도록 설정·보안 배선을 검증한다(키는 더미). */
@AutoConfigureMockMvc
@ActiveProfiles("oauth-github")
@TestPropertySource(properties = ["GITHUB_CLIENT_ID=test-client-id", "GITHUB_CLIENT_SECRET=test-secret"])
class GithubOAuthLoginIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `로그인 시작은 GitHub 인가 화면으로 리다이렉트하고 이메일 스코프와 콜백을 담는다`() {
        mockMvc.get("/oauth2/authorization/github").andExpect {
            status { is3xxRedirection() }
            header {
                string(
                    "Location",
                    allOf(
                        startsWith("https://github.com/login/oauth/authorize"),
                        containsString("client_id=test-client-id"),
                        containsString("user:email"),
                        containsString("/login/oauth2/code/github"),
                    ),
                )
            }
        }
    }

    @Test
    fun `활성 제공자 목록은 비로그인으로 조회되고 켠 제공자만 담는다`() {
        mockMvc.get("/api/auth/oauth2/providers").andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0]") { value("github") }
        }
    }
}
