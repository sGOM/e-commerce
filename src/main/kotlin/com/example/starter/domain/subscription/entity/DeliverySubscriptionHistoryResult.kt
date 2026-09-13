package com.example.starter.domain.subscription.entity

/**
 * 정기배송 회차 처리 결과(append-only 이력, AC10 "회차별 이력"). 기획서 §5 초안(ORDER_CREATED/
 * SKIPPED_OUT_OF_STOCK/PAYMENT_FAILED)에 실제 구현에서 필요해진 두 값을 더했다:
 * - [SKIPPED_BY_USER]: 회원이 스킵을 요청한 회차(AC5)를 재고/결제 실패와 구분해 표시.
 * - [PAUSED_PRODUCT_UNAVAILABLE]: 상품이 완전 판매중지(HIDDEN)되어 구독 자체가 자동 일시정지된 경우
 *   (§4) — 다음 회차부터 재시도해도 소용없으므로 "스킵"이 아니라 "정지"로 구분한다.
 */
enum class DeliverySubscriptionHistoryResult {
    /** 주문이 정상 생성되고 결제까지 성공 */
    ORDER_CREATED,

    /** 재고 부족(또는 일시 품절)으로 이번 회차를 건너뜀 */
    SKIPPED_OUT_OF_STOCK,

    /** 회원이 스킵을 요청해(AC5) 이번 회차를 건너뜀 */
    SKIPPED_BY_USER,

    /** 정기결제 실패로 이번 회차를 건너뜀(연속 누적 시 구독 자동 일시정지, AC8) */
    PAYMENT_FAILED,

    /** 상품 판매중지(HIDDEN)로 구독 자체가 자동 일시정지됨(§4) */
    PAUSED_PRODUCT_UNAVAILABLE,
}
