package com.example.starter.domain.membership.entity

/**
 * 멤버십 라이프사이클 상태.
 *
 * - [ACTIVE]: 정상 구독 중(자동갱신 대상).
 * - [PAST_DUE]: 정기결제 실패로 재시도/유예 중. 유예기간 동안은 혜택을 유지한다(카드 재발급 등
 *   일시적 실패를 흡수하기 위함, 기획서 §4).
 * - [CANCELED]: 해지 예약됨(`canceledAt` 설정). 이미 결제한 기간(`nextBillingAt` 이전)까지는 혜택을
 *   유지하고, 그 시점이 지나면 스케줄러가 [EXPIRED] 로 전이한다(AC3 — 선불 구독의 일반적 관례).
 * - [EXPIRED]: 혜택 종료. 해지 유예 종료 또는 결제 재시도 모두 소진된 최종 상태.
 */
enum class MembershipStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED,
    EXPIRED,
    ;

    /** 이 상태만으로 혜택이 유지되는지(단, [CANCELED] 는 [Membership.nextBillingAt] 과 함께 판정해야 한다). */
    val benefitEligible: Boolean
        get() = this == ACTIVE || this == PAST_DUE || this == CANCELED
}
