package com.example.starter.domain.order.entity

/**
 * 하위 주문(SubOrder, 판매자 단위) 상태. 배송·취소·정산 전이의 단위.
 *
 * 전이: CREATED → PAID → PREPARING → SHIPPED → DELIVERED / CANCELED
 */
enum class SubOrderStatus {
    CREATED,
    PAID,
    PREPARING,
    SHIPPED,
    DELIVERED,
    CANCELED,
}
