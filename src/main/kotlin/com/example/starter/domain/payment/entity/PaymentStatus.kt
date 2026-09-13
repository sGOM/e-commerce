package com.example.starter.domain.payment.entity

/**
 * 결제 상태. 전이: READY → PAID / FAILED, PAID → CANCELED(환불, Phase 5).
 */
enum class PaymentStatus {
    READY, // 결제 생성, 승인 대기
    PAID, // 승인 완료
    FAILED, // 승인 거절
    CANCELED, // 결제 취소/환불
}
