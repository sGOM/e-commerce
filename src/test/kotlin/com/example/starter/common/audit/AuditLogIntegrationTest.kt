package com.example.starter.common.audit

import com.example.starter.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class AuditLogIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var auditLogRepository: AuditLogRepository

    @Test
    fun `요청이 감사 로그로 기록되고 비밀번호는 마스킹된다`() {
        auditLogRepository.deleteAll()

        mockMvc.post("/api/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"audit@example.com","password":"password123","name":"감사대상"}"""
            with(csrf())
        }.andExpect { status { isCreated() } }

        // 비동기 저장이므로 잠시 폴링하며 대기
        val log = awaitAuditLog { it.uri == "/api/auth/signup" }

        assertThat(log.method).isEqualTo("POST")
        assertThat(log.statusCode).isEqualTo(201)
        assertThat(log.durationMs).isGreaterThanOrEqualTo(0)
        // 페이로드에 본문이 기록되되 비밀번호는 마스킹 (jsonb 정규화로 공백이 들어갈 수 있어 포맷 무관 검증)
        assertThat(log.payload).contains("audit@example.com")
        assertThat(log.payload).contains("***")
        assertThat(log.payload).doesNotContain("password123")
    }

    private fun awaitAuditLog(predicate: (AuditLog) -> Boolean): AuditLog {
        repeat(40) {
            auditLogRepository.findAll().firstOrNull(predicate)?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("감사 로그가 기록되지 않았습니다(타임아웃).")
    }
}
