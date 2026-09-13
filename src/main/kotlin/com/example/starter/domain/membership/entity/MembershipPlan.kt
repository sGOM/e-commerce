package com.example.starter.domain.membership.entity

/**
 * 멤버십 플랜. MVP는 [BASIC] 단일 플랜만 판매하며(정책가는 [com.example.starter.domain.membership.entity.MembershipPolicy]
 * 단일 행), [PREMIUM] 은 향후 플랜 다양화(기획서 §8 out-of-scope)를 위한 데이터 모델상의 여지만
 * 확보해 둔 값이다 — 실제 구독 신청 시 PREMIUM 을 선택하면 아직 판매하지 않는 플랜으로 거부한다.
 */
enum class MembershipPlan {
    BASIC,
    PREMIUM,
}
