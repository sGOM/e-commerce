package com.example.starter.domain.order.entity

/**
 * 하위 주문(SubOrder, 판매자 단위) 상태. 배송·취소·정산 전이의 단위.
 *
 * 전이: CREATED → PAID → PREPARING → SHIPPED → DELIVERED / CANCELED
 * 반품(ROADMAP 1.5): SHIPPED·DELIVERED → RETURNING → RETURNED(환불 완료), 거절 시 원래 상태로 복귀.
 */
enum class SubOrderStatus {
    CREATED,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELED,
    RETURNING, // 반품 진행 중(정산 대상에서 빠진다)
    RETURNED, // 반품 완료·환불됨
    ;

    /** 환불까지 끝나 더 이상 진행하지 않는 상태. */
    val isClosed: Boolean
        get() = this == CANCELED || this == RETURNED
}
