package com.example.starter.domain.point.entity

/** 포인트 원장 거래 유형. amount 는 항상 양수이며 부호는 유형이 결정한다. */
enum class PointTransactionType {
    EARN, // 적립 (+)
    USE, // 사용 (-)
    CANCEL_USE, // 사용 취소 환원 (+)
    CANCEL_EARN, // 적립 취소 회수 (-) — 결제 취소/환불 시
    EXPIRE, // 유효기간 만료 (-)
}
