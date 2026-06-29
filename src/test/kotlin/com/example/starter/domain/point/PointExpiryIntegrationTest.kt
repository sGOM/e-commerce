package com.example.starter.domain.point

import com.example.starter.domain.point.entity.PointAccount
import com.example.starter.domain.point.repository.PointAccountRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureMockMvc
@Transactional
class PointExpiryIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var pointAccountRepository: PointAccountRepository
    @Autowired lateinit var pointService: PointService

    private fun seedUserId(email: String): Long =
        userRepository.save(User(email = email, password = "{noop}x", name = email)).id!!

    private fun daysFromNow(d: Long): Instant = Instant.now().plus(d, ChronoUnit.DAYS)

    @Test
    fun `만료일이 지난 적립분은 소멸되고 잔액에서 빠진다`() {
        val userId = seedUserId("expire-1@example.com")
        val account = PointAccount(userId = userId).apply {
            earn(100, null, Instant.now().minus(1, ChronoUnit.DAYS)) // 이미 만료
            earn(50, null, daysFromNow(10)) // 유효
        }
        pointAccountRepository.save(account)
        assertEquals(150, account.balance)

        val expired = pointService.expireDuePoints()

        assertEquals(100, expired)
        assertEquals(50, pointAccountRepository.findByUserId(userId).get().balance)
    }

    @Test
    fun `사용 시 만료 임박 적립분부터 차감된다(FIFO)`() {
        val userId = seedUserId("expire-2@example.com")
        val account = PointAccount(userId = userId).apply {
            earn(100, null, daysFromNow(30)) // 늦게 만료
            earn(50, null, daysFromNow(1)) // 임박
            use(30, null) // 임박분(50)에서 먼저 차감 → 20 남음
        }
        pointAccountRepository.save(account)

        val lots = pointAccountRepository.findWithLotsByUserId(userId).get().lots.sortedBy { it.expiresAt }
        assertEquals(20, lots[0].remaining) // 임박 lot 에서 30 차감
        assertEquals(100, lots[1].remaining) // 늦은 lot 은 그대로
        assertEquals(120, account.balance)
    }

    @Test
    fun `관리자가 만료 처리를 트리거할 수 있다`() {
        val userId = seedUserId("expire-3@example.com")
        pointAccountRepository.save(
            PointAccount(userId = userId).apply { earn(70, null, Instant.now().minus(1, ChronoUnit.DAYS)) },
        )

        mockMvc.post("/api/admin/points/expire") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.expiredTotal") { value(70) }
        }
        assertEquals(0, pointAccountRepository.findByUserId(userId).get().balance)
    }

    @Test
    fun `관리자가 적립 유효기간을 변경할 수 있다`() {
        mockMvc.patch("/api/admin/point-policy") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = org.springframework.http.MediaType.APPLICATION_JSON
            content = """{"expiryDays":30}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.expiryDays") { value(30) }
            jsonPath("$.data.earnRateBp") { value(100) } // 적립률은 변경 안 함(부분 업데이트)
        }
    }
}
