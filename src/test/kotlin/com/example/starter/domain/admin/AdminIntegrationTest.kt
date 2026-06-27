package com.example.starter.domain.admin

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
class AdminIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var roleRepository: RoleRepository

    private fun seedUser(email: String) {
        val role = roleRepository.findByName("ROLE_USER")!!
        userRepository.save(User(email = email, password = "{noop}x", name = email).apply { grantRole(role) })
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun `관리자는 키워드로 사용자를 동적 검색한다`() {
        seedUser("admintest-alice@example.com")
        seedUser("admintest-bob@example.com")

        mockMvc.get("/api/admin/users?keyword=admintest-alice").andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].email") { value("admintest-alice@example.com") }
        }
    }

    @Test
    @WithMockUser(roles = ["USER"])
    fun `일반 사용자가 관리자 자원에 접근하면 403`() {
        mockMvc.get("/api/admin/users").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("AUTH-002") }
        }
    }

    @Test
    fun `미인증 상태로 관리자 자원에 접근하면 401`() {
        mockMvc.get("/api/admin/audit-logs").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH-001") }
        }
    }
}
