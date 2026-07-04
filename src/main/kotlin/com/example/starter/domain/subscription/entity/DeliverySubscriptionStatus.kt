package com.example.starter.domain.subscription.entity

/**
 * 정기배송 구독 상태(`docs/planning/subscription-delivery.md` §5).
 *
 * 멤버십([com.example.starter.domain.membership.entity.MembershipStatus])과 달리 결제 유예 상태
 * (PAST_DUE)를 두지 않는다 — 정기배송은 실패 시 해당 회차를 "스킵"하고 다음 주기로 넘어가는 것이
 * 원칙(AC7/AC8)이라 유예기간 동안 재시도를 기다릴 이유가 없다. 연속 실패가 임계치를 넘으면 바로
 * [PAUSED] 로 전이한다.
 */
enum class DeliverySubscriptionStatus {
    /** 정상 진행 중 — 배치가 다음 회차를 자동 처리한다. */
    ACTIVE,

    /** 일시정지(회원 요청 또는 연속 결제실패/상품 판매중단으로 자동 전환) — 자동 주문 생성 없음. */
    PAUSED,

    /** 해지 — 예정된 다음 회차부터 생성되지 않는다(이미 생성된 주문은 영향 없음, AC4). */
    CANCELED,
}
