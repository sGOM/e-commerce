package com.example.starter.domain.settlement.dto

import com.example.starter.domain.settlement.entity.Settlement
import com.example.starter.domain.settlement.entity.SettlementStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant

/** 정산서 응답 */
data class SettlementResponse(
    val settlementId: Long,
    val sellerId: Long,
    val storeName: String,
    val salesAmount: Long,
    val commissionAmount: Long,
    val payoutAmount: Long,
    val settledCount: Int,
    val status: SettlementStatus,
    val paidAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(settlement: Settlement) = SettlementResponse(
            settlementId = requireNotNull(settlement.id),
            sellerId = requireNotNull(settlement.seller.id),
            storeName = settlement.seller.storeName,
            salesAmount = settlement.salesAmount,
            commissionAmount = settlement.commissionAmount,
            payoutAmount = settlement.payoutAmount,
            settledCount = settlement.settledCount,
            status = settlement.status,
            paidAt = settlement.paidAt,
            createdAt = settlement.createdAt,
        )
    }
}

/** 수수료 정책 응답 */
data class SettlementPolicyResponse(
    val commissionRateBp: Int,
)

/** 수수료율 변경 요청(관리자). basis point(1000 = 10%). */
data class UpdateSettlementPolicyRequest(
    @field:NotNull
    @field:PositiveOrZero
    @field:Max(10_000)
    val commissionRateBp: Int?,
)
