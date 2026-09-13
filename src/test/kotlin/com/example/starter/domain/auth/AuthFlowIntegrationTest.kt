package com.example.starter.domain.auth

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.oauth.CustomOAuth2User
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.hamcrest.Matchers.hasItem
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc

@AutoConfigureMockMvc
class AuthFlowIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Test
    fun `소셜 로그인 사용자도 내 정보를 조회할 수 있다`() {
        val saved = userRepository.save(
            User(email = "oauth-me-${System.nanoTime()}@example.com", password = "{noop}x", name = "소셜"),
        )
        val principal = CustomOAuth2User(saved, mapOf("sub" to "provider-1"), "provider-1")

        mockMvc.get("/api/auth/me") {
            with(oauth2Login().oauth2User(principal))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.email") { value(saved.email) }
        }
    }

    @Test
    fun `회원가입 후 로그인하면 세션으로 내 정보를 조회할 수 있다`() {
        // 이 클래스는 롤백하지 않으므로(세션 로그인 흐름) 재사용 DB 에서도 충돌하지 않게 고유 이메일을 쓴다.
        val email = "flow-${System.nanoTime()}@example.com"

        // 1) 회원가입
        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123","name":"홍길동"}"""
            with(csrf())
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.email") { value(email) }
            jsonPath("$.data.roles[0]") { value("ROLE_USER") }
        }

        // 2) 로그인 — 세션 확보
        val session = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123"}"""
            with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.email") { value(email) }
        }.andReturn().request.session as MockHttpSession

        // 3) 세션으로 내 정보 조회
        mockMvc.get("/api/auth/me") {
            this.session = session
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.email") { value(email) }
        }
    }

    @Test
    fun `판매자 승인 후 재로그인 없이 내 정보 조회로 세션 권한이 갱신된다`() {
        val email = "role-refresh-${System.nanoTime()}@example.com"
        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123","name":"입점자"}"""
            with(csrf())
        }.andExpect { status { isCreated() } }
        val session = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123"}"""
            with(csrf())
        }.andExpect { status { isOk() } }.andReturn().request.session as MockHttpSession

        val res = mockMvc.post("/api/seller/apply") {
            this.session = session
            contentType = MediaType.APPLICATION_JSON
            content = """{"storeName":"refresh-store-${System.nanoTime()}"}"""
            with(csrf())
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val sellerId = Regex(""""sellerId":(\d+)""").find(res)!!.groupValues[1].toLong()

        // 승인 전: 로그인 시점 권한이라 판매자 자원 접근 불가
        mockMvc.get("/api/seller/store") { this.session = session }.andExpect { status { isForbidden() } }

        mockMvc.patch("/api/admin/sellers/$sellerId/approve") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"approved":true}"""
        }.andExpect { status { isOk() } }

        // 같은 세션으로 내 정보 조회 → DB 역할을 다시 읽어 세션 권한까지 갱신
        mockMvc.get("/api/auth/me") { this.session = session }.andExpect {
            status { isOk() }
            jsonPath("$.data.roles") { value(hasItem("ROLE_SELLER")) }
        }
        mockMvc.get("/api/seller/store") { this.session = session }.andExpect { status { isOk() } }
    }

    @Test
    fun `비밀번호를 변경하면 새 비밀번호로만 로그인된다`() {
        val email = "pw-change-${System.nanoTime()}@example.com"
        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123","name":"변경자"}"""
            with(csrf())
        }.andExpect { status { isCreated() } }
        val session = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123"}"""
            with(csrf())
        }.andExpect { status { isOk() } }.andReturn().request.session as MockHttpSession

        // 현재 비밀번호 불일치
        mockMvc.patch("/api/auth/password") {
            this.session = session; with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"wrong-pass","newPassword":"newpass456"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("USER-004") }
        }
        // 새 비밀번호 길이 규칙(가입과 동일 8~64자)
        mockMvc.patch("/api/auth/password") {
            this.session = session; with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"password123","newPassword":"short"}"""
        }.andExpect { status { isBadRequest() } }

        mockMvc.patch("/api/auth/password") {
            this.session = session; with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"currentPassword":"password123","newPassword":"newpass456"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"password123"}"""
            with(csrf())
        }.andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"newpass456"}"""
            with(csrf())
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `소셜 전용 계정은 현재 비밀번호 없이 비밀번호를 설정할 수 있다`() {
        val saved = userRepository.save(
            User(email = "oauth-pw-${System.nanoTime()}@example.com", password = null, name = "소셜"),
        )
        val principal = CustomOAuth2User(saved, mapOf("sub" to "provider-3"), "provider-3")

        mockMvc.patch("/api/auth/password") {
            with(oauth2Login().oauth2User(principal)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"newPassword":"newpass456"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"${saved.email}","password":"newpass456"}"""
            with(csrf())
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `미인증 상태로 보호 자원 접근 시 401 JSON 을 반환한다`() {
        mockMvc.get("/api/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.code") { value("AUTH-001") }
        }
    }

    @Test
    fun `중복 이메일로 회원가입하면 409 를 반환한다`() {
        val body = """{"email":"dup-${System.nanoTime()}@example.com","password":"password123","name":"중복"}"""
        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON; content = body; with(csrf())
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON; content = body; with(csrf())
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USER-002") }
        }
    }
}
