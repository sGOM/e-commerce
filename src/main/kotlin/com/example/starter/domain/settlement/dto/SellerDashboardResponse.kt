package com.example.starter.domain.settlement.dto

import java.time.LocalDate

/** 판매자 매출 대시보드 요약 */
data class SellerDashboardResponse(
    val from: LocalDate,
    val to: LocalDate,
    /** 기간 내 생성된 주문 중 결제 이후 상태(취소 제외) 건수 */
    val orderCount: Long,
    /** 위 주문의 판매액 합계 */
    val salesAmount: Long,
    /** 기간 무관, 아직 정산서에 묶이지 않은 판매액 */
    val unsettledAmount: Long,
    /** 기간 무관, 정산서가 만들어졌지만 지급 전인 금액(수수료 차감 후) */
    val pendingPayoutAmount: Long,
)
