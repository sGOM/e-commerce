package com.example.starter.domain.point

import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class PointPolicyAdminIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `관리자는 적립률을 조회하고 변경할 수 있다`() {
        // 기본 정책 1%(100bp)
        mockMvc.get("/api/admin/point-policy") {
            with(user("admin").roles("ADMIN"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.earnRateBp") { value(100) }
        }

        // 3%(300bp)로 변경
        mockMvc.patch("/api/admin/point-policy") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"earnRateBp":300}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.earnRateBp") { value(300) }
        }
    }

    @Test
    fun `일반 회원은 적립률을 변경할 수 없다`() {
        mockMvc.patch("/api/admin/point-policy") {
            with(user("member").roles("CUSTOMER")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"earnRateBp":300}"""
        }.andExpect {
            status { isForbidden() }
        }
    }
}
