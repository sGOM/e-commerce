package com.example.starter.domain.order.entity

/**
 * 주문(Order) 전체 상태 — 결제 단위. 배송 등 세부 진행은 [SubOrderStatus] 가 담당한다.
 *
 * Phase 3(주문 생성)에서는 [CREATED] 로만 만들어지며, 결제(Phase 4) 시 [PAID] 로 전이한다.
 */
enum class OrderStatus {
    CREATED, // 주문 생성·재고 예약 완료, 결제 대기
    PAID, // 결제 완료
    CANCELED, // 취소/환불(재고·쿠폰·포인트 복원 완료)
}
