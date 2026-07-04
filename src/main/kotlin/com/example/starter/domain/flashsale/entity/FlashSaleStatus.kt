package com.example.starter.domain.flashsale.entity

/**
 * 타임딜의 "행정 상태" — 관리자가 강제 종료했는지만 표현한다.
 *
 * 시작 전(예정)/진행 중/시간 만료/한도 소진 같은 "지금 이 시각의 진행 단계"는 이 컬럼이 아니라
 * [FlashSale.phaseAt] 이 [startAt]/[endAt]/[soldQuantity] 로부터 매 조회 시점에 파생 계산한다
 * (배치 잡 없이 실시간으로 정확한 상태를 유지하기 위함, [FlashSalePhase] 참고).
 */
enum class FlashSaleStatus {
    ACTIVE,
    CANCELED,
}
