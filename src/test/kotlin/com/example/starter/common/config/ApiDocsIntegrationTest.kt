package com.example.starter.common.config

import com.example.starter.support.AbstractIntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class ApiDocsIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `비로그인 상태에서도 OpenAPI 문서와 Swagger UI 를 조회할 수 있다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            content { string(containsString("/api/products")) }
        }
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isOk() } }
    }
}
