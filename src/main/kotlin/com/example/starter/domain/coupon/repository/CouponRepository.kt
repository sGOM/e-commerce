package com.example.starter.domain.coupon.repository

import com.example.starter.domain.coupon.entity.Coupon
import org.springframework.data.jpa.repository.JpaRepository

interface CouponRepository : JpaRepository<Coupon, Long>
