package com.example.starter.domain.flashsale.entity

/**
 * 타임딜의 실시간 진행 단계(고객 노출용, 파생 계산 — [FlashSale.phaseAt]).
 *
 * 기획서(§4 한도 소진 표시)에 따라 "한도 소진"과 "시간 만료"는 고객에게 동일하게 "딜 종료"로
 * 표기한다(재고 품절과는 별개 개념 — 재고는 남아있고 정가로는 구매 가능할 수 있음).
 */
enum class FlashSalePhase {
    SCHEDULED, // 시작 전(오픈 예정)
    ONGOING, // 진행 중 — 특가 적용 가능
    ENDED, // 종료 시각 경과 또는 한도 수량 소진
    CANCELED, // 관리자 강제 종료
}
