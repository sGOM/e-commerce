package com.example.starter.domain.loyalty

import com.example.starter.domain.coupon.entity.Coupon
import com.example.starter.domain.coupon.entity.DiscountType
import com.example.starter.domain.coupon.repository.CouponRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import com.example.starter.domain.loyalty.entity.LoyaltyTier
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 로열티 등급 승급 쿠폰 자동 발급의 멱등성을 검증한다(오너 결정 2026-07-05).
 *
 * [LoyaltyCouponProperties] 는 운영 설정(application.yml)에서 등급→쿠폰id 매핑을 주입받는 빈이지만,
 * 이 테스트는 쿠폰 id 를 테스트마다 동적으로 생성해야 해서 Spring 프로퍼티 바인딩 대신 테스트
 * 안에서 직접 인스턴스를 만들어 [LoyaltyTierBenefitService] 에 주입한다(순수 Kotlin 객체 조립 —
 * DI 컨테이너를 거치지 않아도 리포지토리는 실제 Spring 빈을 그대로 재사용할 수 있다).
 */
@Transactional
class LoyaltyTierBenefitServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var issuedCouponRepository: IssuedCouponRepository

    private fun seedUser(email: String): Long =
        userRepository.save(User(email = email, password = "{noop}x", name = email)).id!!

    private fun seedCoupon(name: String): Long =
        couponRepository.save(
            Coupon(
                name = name,
                discountType = DiscountType.FIXED,
                discountValue = 5_000,
                validFrom = Instant.now().minusSeconds(3600),
                validUntil = Instant.now().plusSeconds(3600 * 24 * 30),
            ),
        ).id!!

    @Test
    fun `등급 매핑에 쿠폰이 지정되어 있으면 승급 시 쿠폰을 발급한다`() {
        val userId = seedUser("loyalty-coupon-1@example.com")
        val couponId = seedCoupon("GOLD 승급 쿠폰")
        val benefitService = LoyaltyTierBenefitService(
            LoyaltyCouponProperties(couponIdByTier = mapOf(LoyaltyTier.GOLD to couponId)),
            couponRepository,
            issuedCouponRepository,
        )

        benefitService.grantUpgradeCoupon(userId, LoyaltyTier.GOLD)

        val issued = issuedCouponRepository.findByUserIdOrderByIdDesc(userId)
        assertEquals(1, issued.size)
        assertEquals(couponId, issued.first().coupon.id)
    }

    @Test
    fun `같은 등급 쿠폰이 이미 발급되었으면 다시 발급하지 않는다(멱등)`() {
        val userId = seedUser("loyalty-coupon-2@example.com")
        val couponId = seedCoupon("VIP 승급 쿠폰")
        val benefitService = LoyaltyTierBenefitService(
            LoyaltyCouponProperties(couponIdByTier = mapOf(LoyaltyTier.VIP to couponId)),
            couponRepository,
            issuedCouponRepository,
        )

        // 같은 승급이 배치 재실행 등으로 여러 번 감지되어도(BRONZE→VIP→GOLD→VIP 오르내림 포함)
        // 쿠폰은 1회만 발급된다.
        benefitService.grantUpgradeCoupon(userId, LoyaltyTier.VIP)
        benefitService.grantUpgradeCoupon(userId, LoyaltyTier.VIP)
        benefitService.grantUpgradeCoupon(userId, LoyaltyTier.VIP)

        val issued = issuedCouponRepository.findByUserIdOrderByIdDesc(userId)
        assertEquals(1, issued.size)
    }

    @Test
    fun `등급에 매핑된 쿠폰이 없으면 아무것도 발급하지 않는다`() {
        val userId = seedUser("loyalty-coupon-3@example.com")
        val benefitService = LoyaltyTierBenefitService(
            LoyaltyCouponProperties(couponIdByTier = emptyMap()), // 아직 운영에서 쿠폰을 안 만든 상태
            couponRepository,
            issuedCouponRepository,
        )

        benefitService.grantUpgradeCoupon(userId, LoyaltyTier.SILVER)

        assertTrue(issuedCouponRepository.findByUserIdOrderByIdDesc(userId).isEmpty())
    }

    @Test
    fun `매핑된 쿠폰 정의가 실제로 존재하지 않으면 예외 없이 스킵한다`() {
        val userId = seedUser("loyalty-coupon-4@example.com")
        val benefitService = LoyaltyTierBenefitService(
            LoyaltyCouponProperties(couponIdByTier = mapOf(LoyaltyTier.SILVER to 999_999_999L)), // 존재하지 않는 id
            couponRepository,
            issuedCouponRepository,
        )

        benefitService.grantUpgradeCoupon(userId, LoyaltyTier.SILVER) // 예외를 던지지 않아야 한다

        assertTrue(issuedCouponRepository.findByUserIdOrderByIdDesc(userId).isEmpty())
    }
}
