package com.example.starter.domain.coupon

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.coupon.dto.MembershipCouponResponse
import com.example.starter.domain.coupon.entity.IssuedCoupon
import com.example.starter.domain.coupon.repository.CouponRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import com.example.starter.domain.membership.MembershipBenefitService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 멤버십 전용 쿠폰 수령(`docs/planning/subscription-membership.md` AC10). 무료배송을 없앤 대신(ROADMAP 7.3)
 * 관리자가 여는 멤버십 이벤트 쿠폰을 혜택 활성 회원이 직접 받는다. 혜택 판정은 수령 시점 기준이며,
 * 받은 뒤의 사용 조건은 일반 쿠폰과 같다(해지해도 이미 받은 쿠폰은 유지).
 */
@Service
@Transactional(readOnly = true)
class MembershipCouponService(
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val membershipBenefitService: MembershipBenefitService,
) {

    fun getClaimable(userId: Long): List<MembershipCouponResponse> =
        couponRepository.findClaimableMembershipCoupons(Instant.now()).map { coupon ->
            MembershipCouponResponse.from(coupon, issuedCouponRepository.existsByCouponIdAndUserId(coupon.id!!, userId))
        }

    @Transactional
    fun claim(userId: Long, couponId: Long) {
        val now = Instant.now()
        val coupon = couponRepository.findById(couponId).orElse(null)
            ?.takeIf { it.membershipOnly && it.isValidAt(now) }
            ?: throw BusinessException(ErrorCode.COUPON_NOT_FOUND)
        if (!membershipBenefitService.isBenefitActive(userId, now)) {
            throw BusinessException(ErrorCode.COUPON_MEMBERSHIP_REQUIRED)
        }
        // 동시 요청은 uq_issued_coupons_coupon_user 가 막는다
        if (issuedCouponRepository.existsByCouponIdAndUserId(couponId, userId)) {
            throw BusinessException(ErrorCode.COUPON_ALREADY_CLAIMED)
        }
        issuedCouponRepository.save(IssuedCoupon(coupon = coupon, userId = userId))
    }
}
