package com.example.starter.domain.coupon.repository

import com.example.starter.domain.coupon.entity.IssuedCoupon
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface IssuedCouponRepository : JpaRepository<IssuedCoupon, Long> {

    /** 발급분 + 쿠폰 정의를 함께 로딩(할인 계산/표시용) */
    @EntityGraph(attributePaths = ["coupon"])
    fun findWithCouponByIdAndUserId(id: Long, userId: Long): Optional<IssuedCoupon>

    @EntityGraph(attributePaths = ["coupon"])
    fun findByUserIdOrderByIdDesc(userId: Long): List<IssuedCoupon>

    /** 주문에 사용된 발급쿠폰(취소 시 복원용) */
    fun findByOrderId(orderId: Long): Optional<IssuedCoupon>
}
