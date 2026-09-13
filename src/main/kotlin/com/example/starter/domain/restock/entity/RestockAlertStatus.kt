package com.example.starter.domain.restock.entity

/**
 * 재입고 알림 신청 상태.
 *
 * - [PENDING]: 재입고 대기 중. 사용자·옵션 조합으로 유니크(부분 유니크 인덱스, `restock_alerts` 참고).
 * - [NOTIFIED]: 발송 완료 — 소멸성 상태다. 재입고를 다시 받고 싶으면 재신청해야 한다(스팸 방지 및
 *   데이터 단순화, `docs/planning/restock-alert.md` AC6).
 * - [CANCELED]: 사용자가 직접 취소.
 */
enum class RestockAlertStatus {
    PENDING,
    NOTIFIED,
    CANCELED,
}
