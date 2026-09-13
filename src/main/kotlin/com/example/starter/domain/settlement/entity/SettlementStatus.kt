package com.example.starter.domain.settlement.entity

/** 정산 상태. */
enum class SettlementStatus {
    PENDING, // 정산서 생성, 지급 대기
    PAID, // 판매대금 지급 완료
}
