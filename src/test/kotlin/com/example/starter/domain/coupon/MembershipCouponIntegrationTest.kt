package com.example.starter.domain.coupon

import com.example.starter.domain.coupon.entity.Coupon
import com.example.starter.domain.coupon.entity.DiscountType
import com.example.starter.domain.coupon.repository.CouponRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import com.example.starter.domain.membership.entity.Membership
import com.example.starter.domain.membership.repository.MembershipRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/** 멤버십 전용 쿠폰(`docs/planning/subscription-membership.md` AC10) — 멤버십 혜택 활성 회원만 직접 받는다. */
@AutoConfigureMockMvc
@Transactional
class MembershipCouponIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var membershipRepository: MembershipRepository
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var issuedCouponRepository: IssuedCouponRepository

    private val now = Instant.now()

    private fun seedUser(email: String, member: Boolean): CustomUserDetails {
        val user = userRepository.save(User(email = email, password = "{noop}x", name = email))
        if (member) membershipRepository.save(Membership(userId = user.id!!))
        return CustomUserDetails(user)
    }

    private fun seedCoupon(name: String, membershipOnly: Boolean = true, validUntil: Instant = now.plus(Duration.ofDays(7))) =
        couponRepository.save(
            Coupon(
                name = name,
                discountType = DiscountType.FIXED,
                discountValue = 3_000,
                validFrom = now.minus(Duration.ofDays(1)),
                validUntil = validUntil,
                membershipOnly = membershipOnly,
            ),
        ).id!!

    private fun claim(member: CustomUserDetails, couponId: Long) =
        mockMvc.post("/api/me/membership/coupons/$couponId/claim") { with(user(member)); with(csrf()) }

    @Test
    fun `멤버십 회원은 받을 수 있는 전용 쿠폰 목록을 보고 한 번만 받는다`() {
        val member = seedUser("mc-member@example.com", member = true)
        val couponId = seedCoupon("멤버십 3천원")
        seedCoupon("일반 쿠폰", membershipOnly = false)
        seedCoupon("지난 쿠폰", validUntil = now.minus(Duration.ofHours(1)))

        mockMvc.get("/api/me/membership/coupons") { with(user(member)) }.andExpect {
            jsonPath("$.data[?(@.couponId == $couponId)].claimed") { value(false) }
            jsonPath("$.data[?(@.name == '일반 쿠폰')]") { isEmpty() }
            jsonPath("$.data[?(@.name == '지난 쿠폰')]") { isEmpty() }
        }

        claim(member, couponId).andExpect { status { isOk() } }
        claim(member, couponId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("COUPON-006") }
        }

        assertThat(issuedCouponRepository.findByUserIdOrderByIdDesc(member.userId).map { it.coupon.id }).containsExactly(couponId)
        mockMvc.get("/api/me/membership/coupons") { with(user(member)) }.andExpect {
            jsonPath("$.data[?(@.couponId == $couponId)].claimed") { value(true) }
        }
    }

    @Test
    fun `멤버십이 아니면 전용 쿠폰을 받을 수 없다`() {
        val nonMember = seedUser("mc-none@example.com", member = false)
        val couponId = seedCoupon("멤버십 전용")

        claim(nonMember, couponId).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COUPON-005") }
        }
    }

    @Test
    fun `일반 쿠폰이나 지난 쿠폰은 이 경로로 받을 수 없다`() {
        val member = seedUser("mc-other@example.com", member = true)

        claim(member, seedCoupon("일반", membershipOnly = false)).andExpect { status { isNotFound() } }
        claim(member, seedCoupon("지난", validUntil = now.minus(Duration.ofHours(1)))).andExpect { status { isNotFound() } }
    }

    @Test
    fun `관리자는 멤버십 전용 쿠폰을 발행한다`() {
        mockMvc.post("/api/admin/coupons") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"멤버십 데이","discountType":"RATE","discountValue":10,"maxDiscountAmount":5000,
                "validFrom":"$now","validUntil":"${now.plus(Duration.ofDays(3))}","membershipOnly":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.membershipOnly") { value(true) }
        }
    }
}
