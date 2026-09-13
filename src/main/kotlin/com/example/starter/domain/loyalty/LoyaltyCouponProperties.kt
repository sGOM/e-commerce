package com.example.starter.domain.loyalty

import com.example.starter.domain.loyalty.entity.LoyaltyTier
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 등급 승급 혜택(등급 전용 쿠폰) 매핑 설정. 오너 확정(2026-07-05): 로열티 등급 혜택은
 * **등급 승급 시 해당 등급 전용 쿠폰 자동 발급 하나만** 구현한다(무료배송 등은 out of scope,
 * [LoyaltyTier] 문서 참고).
 *
 * 쿠폰 "정의"는 새 개념을 추가하지 않고 기존 [com.example.starter.domain.coupon.entity.Coupon] 을
 * 그대로 재사용한다 — 운영자가 관리자 쿠폰 발행 API(`POST /api/admin/coupons`, 발급 대상 없이 정의만
 * 생성)로 등급별 전용 쿠폰을 미리 만들고, 그 쿠폰 id 를 여기 매핑에 등록하면
 * [LoyaltyTierBenefitService] 가 승급 시 해당 회원에게 자동 발급([com.example.starter.domain.coupon.entity.IssuedCoupon])한다.
 *
 * 매핑이 없는 등급(기본: 전부 비어있음, 특히 BRONZE는 승급 개념이 없어 항상 매핑하지 않는다)은
 * 쿠폰을 발급하지 않는다 — 운영에서 실제 쿠폰을 만들기 전까지는 안전하게 무동작.
 */
@ConfigurationProperties(prefix = "loyalty-coupon")
data class LoyaltyCouponProperties(
    /** 등급 → 승급 축하 쿠폰 정의(Coupon) id. */
    val couponIdByTier: Map<LoyaltyTier, Long> = emptyMap(),
)
