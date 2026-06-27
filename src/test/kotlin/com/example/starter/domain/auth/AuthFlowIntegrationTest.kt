package com.example.starter.domain.auth

import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc

@AutoConfigureMockMvc
class AuthFlowIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `회원가입 후 로그인하면 세션으로 내 정보를 조회할 수 있다`() {
        val email = "flow@example.com"

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
    fun `미인증 상태로 보호 자원 접근 시 401 JSON 을 반환한다`() {
        mockMvc.get("/api/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.code") { value("AUTH-001") }
        }
    }

    @Test
    fun `중복 이메일로 회원가입하면 409 를 반환한다`() {
        val body = """{"email":"dup@example.com","password":"password123","name":"중복"}"""
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
