package com.example.starter.domain.loyalty.entity

/**
 * 누적구매 등급(로열티 티어). 최근 12개월 순구매액(실결제액, 취소/환불 제외) 기준 4단계.
 *
 * **유료 구독형 [com.example.starter.domain.membership.entity.Membership] 과는 별개 개념**이다 —
 * 그쪽은 "포인트 적립 배수"를 다루고, 이 등급은 별개 혜택으로 차별화한다(개념 충돌 회피,
 * product-planner 브레인스토밍 §0 우려사항 반영).
 *
 * **오너 확정(2026-07-05): 등급 혜택은 "등급 승급 시 등급 전용 쿠폰 자동 발급" 하나만 구현한다.**
 * 무료배송 등 그 외 혜택은 이번 범위에서 **명시적으로 out of scope** 다(추후 별도 기획/구현 필요).
 *
 * 구현 위치:
 * - [com.example.starter.domain.loyalty.LoyaltyCouponProperties] — 등급 → 쿠폰(Coupon) id 매핑(설정).
 * - [com.example.starter.domain.loyalty.LoyaltyTierBenefitService] — 승급 감지 시 멱등하게 쿠폰 발급
 *   (기존 [com.example.starter.domain.coupon.entity.Coupon]/[com.example.starter.domain.coupon.entity.IssuedCoupon]
 *   재사용, 새 엔티티 없음).
 * - [com.example.starter.domain.loyalty.LoyaltyTierBatchService] — 승급 확정 후 별도 트랜잭션으로
 *   쿠폰 발급을 호출해, 쿠폰 발급 실패가 등급 갱신이나 배치 전체에 영향을 주지 않게 격리.
 */
enum class LoyaltyTier(val displayName: String) {
    BRONZE("브론즈"),
    SILVER("실버"),
    GOLD("골드"),
    VIP("VIP"),
    ;

    /** 다음 상위 등급. 최상위(VIP)는 null. */
    fun next(): LoyaltyTier? = entries.getOrNull(ordinal + 1)
}
