package com.example.starter.domain.coupon

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.coupon.dto.IssuedCouponResponse
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 쿠폰 적용/복원. 발급쿠폰(1회용)을 주문에 적용해 할인액을 계산하고 사용 처리한다.
 * 결제 실패/주문 취소 시 [restoreForOrder] 로 미사용 복원한다. 게스트는 쿠폰을 사용할 수 없다(회원 전용).
 */
@Service
@Transactional(readOnly = true)
class CouponService(
    private val issuedCouponRepository: IssuedCouponRepository,
) {

    /** 주문에 쿠폰을 적용하고 할인액을 반환한다(사용 처리 포함). 검증 실패 시 예외로 주문 트랜잭션을 롤백. */
    @Transactional
    fun applyToOrder(userId: Long, issuedCouponId: Long, orderId: Long, orderAmount: Long): Long {
        val issued = issuedCouponRepository.findWithCouponByIdAndUserId(issuedCouponId, userId)
            .orElseThrow { BusinessException(ErrorCode.COUPON_NOT_FOUND) }
        if (issued.used) {
            throw BusinessException(ErrorCode.COUPON_ALREADY_USED)
        }
        val coupon = issued.coupon
        if (!coupon.isValidAt(Instant.now())) {
            throw BusinessException(ErrorCode.COUPON_EXPIRED)
        }
        if (!coupon.meetsMinOrderAmount(orderAmount)) {
            throw BusinessException(ErrorCode.COUPON_MIN_ORDER_NOT_MET)
        }
        issued.use(orderId)
        return coupon.calculateDiscount(orderAmount)
    }

    /** 주문에 사용된 쿠폰을 미사용으로 복원(취소/결제 실패). */
    @Transactional
    fun restoreForOrder(orderId: Long) {
        issuedCouponRepository.findByOrderId(orderId).ifPresent { it.restore() }
    }

    fun getMyCoupons(userId: Long): List<IssuedCouponResponse> =
        issuedCouponRepository.findByUserIdOrderByIdDesc(userId).map { IssuedCouponResponse.from(it) }
}
