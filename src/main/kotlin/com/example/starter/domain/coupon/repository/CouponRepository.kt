package com.example.starter.domain.coupon.repository

import com.example.starter.domain.coupon.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CouponRepository : JpaRepository<Coupon, Long> {

    /** 지금 받을 수 있는 멤버십 전용 쿠폰(유효기간 안), 마감 임박 순. */
    @Query(
        """
        select c from Coupon c
        where c.membershipOnly = true and c.validFrom <= :now and c.validUntil >= :now
        order by c.validUntil asc, c.id asc
        """,
    )
    fun findClaimableMembershipCoupons(@Param("now") now: Instant): List<Coupon>
}
