package com.example.starter.domain.returns.entity

/** 반품 사유. 단순변심만 구매자가 반품 배송비를 부담한다(2026-09-30 정책). */
enum class ReturnReason(val buyerPaysFee: Boolean) {
    CHANGE_OF_MIND(true),
    DEFECTIVE(false),
    WRONG_DELIVERY(false),
}

/** 반품 진행 상태. REQUESTED → COLLECTING(회수 중) → COMPLETED(검수 통과·환불) / REJECTED(요청·검수 거절). */
enum class ReturnStatus {
    REQUESTED,
    COLLECTING,
    COMPLETED,
    REJECTED,
}
