package com.example.starter.domain.loyalty.entity

/**
 * 누적구매 등급(로열티 티어). 최근 12개월 순구매액(실결제액, 취소/환불 제외) 기준 4단계.
 *
 * **유료 구독형 [com.example.starter.domain.membership.entity.Membership] 과는 별개 개념**이다 —
 * 그쪽은 "포인트 적립 배수"를 다루고, 이 등급은 **등급 전용 쿠폰 발급 / 무료배송** 등으로 혜택을
 * 차별화한다(개념 충돌 회피, product-planner 브레인스토밍 §0 우려사항 반영).
 *
 * TODO(오너 결정 필요): 등급별 혜택의 실제 적용 지점(쿠폰 자동 발급 트리거, 배송비 계산 시 등급 확인)은
 * 이번 범위에서 배선하지 않았다. 후보 연동 지점:
 * - 등급 상승 시 [com.example.starter.domain.coupon] 도메인에 등급 전용 쿠폰 자동 발급
 *   (`LoyaltyTierBatchService.recalculate` 에서 승급 감지 시 이벤트 발행 → 쿠폰 발급 리스너 구독)
 * - 결제/배송비 계산 시 [com.example.starter.domain.delivery] 도메인에서 등급 조회 후 무료배송 적용
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
