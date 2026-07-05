package com.example.starter.domain.loyalty

import com.example.starter.domain.coupon.entity.IssuedCoupon
import com.example.starter.domain.coupon.repository.CouponRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import com.example.starter.domain.loyalty.entity.LoyaltyTier
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로열티 등급 승급 혜택(등급 전용 쿠폰 자동 발급) 처리. [LoyaltyTierBatchService] 가 승급을 감지하면
 * 호출한다. 등급 상승 시에만 호출되며(강등/동일 등급 유지 시 호출 안 됨), [couponIdByTier] 설정에
 * 매핑된 쿠폰이 없으면 조용히 아무것도 하지 않는다(운영에서 쿠폰을 아직 안 만든 상태에 안전).
 *
 * 멱등성: 같은 회원이 같은 등급 쿠폰을 이미 발급받았으면([IssuedCouponRepository.existsByCouponIdAndUserId])
 * 다시 발급하지 않는다 — 배치가 여러 번 돌거나(BRONZE→GOLD→SILVER→GOLD 처럼 등급을 오르내리는
 * 케이스 포함) 같은 승급이 반복 감지돼도 중복 발급을 막는다.
 */
@Service
@Transactional(readOnly = true)
class LoyaltyTierBenefitService(
    private val loyaltyCouponProperties: LoyaltyCouponProperties,
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 승급 축하 쿠폰 발급 시도. 설정 미비/쿠폰 정의 부재는 예외 없이 로그만 남기고 스킵한다. */
    @Transactional
    fun grantUpgradeCoupon(userId: Long, newTier: LoyaltyTier) {
        val couponId = loyaltyCouponProperties.couponIdByTier[newTier] ?: return
        val coupon = couponRepository.findById(couponId).orElse(null)
        if (coupon == null) {
            log.warn("로열티 등급({}) 전용 쿠폰 설정(id={})에 해당하는 쿠폰 정의를 찾을 수 없습니다", newTier, couponId)
            return
        }
        if (issuedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            return // 이미 발급됨(멱등)
        }
        issuedCouponRepository.save(IssuedCoupon(coupon = coupon, userId = userId))
        log.info("로열티 등급 승급 쿠폰 발급: userId={}, tier={}, couponId={}", userId, newTier, couponId)
    }
}
