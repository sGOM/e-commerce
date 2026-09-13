package com.example.starter.domain.point.dto

import com.example.starter.domain.point.entity.PointAccount
import com.example.starter.domain.point.entity.PointTransaction
import com.example.starter.domain.point.entity.PointTransactionType
import java.time.Instant

/** 포인트 원장 항목 응답 */
data class PointTransactionResponse(
    val type: PointTransactionType,
    val amount: Long,
    val orderId: Long?,
    val createdAt: Instant,
) {
    companion object {
        fun from(txn: PointTransaction) = PointTransactionResponse(
            type = txn.type,
            amount = txn.amount,
            orderId = txn.orderId,
            createdAt = txn.createdAt,
        )
    }
}

/** 내 포인트 잔액 + 이력 응답 */
data class PointSummaryResponse(
    val balance: Long,
    val transactions: List<PointTransactionResponse>,
) {
    companion object {
        fun from(account: PointAccount?) = PointSummaryResponse(
            balance = account?.balance ?: 0,
            transactions = account?.transactions
                ?.sortedByDescending { it.id }
                ?.map { PointTransactionResponse.from(it) }
                ?: emptyList(),
        )
    }
}

/** 포인트 적립 정책 응답(관리자) */
data class PointPolicyResponse(
    val earnRateBp: Int,
    val expiryDays: Int,
)

/** 포인트 정책 변경 요청(관리자). 전달한 항목만 변경한다(부분 업데이트). */
data class UpdatePointPolicyRequest(
    @field:jakarta.validation.constraints.PositiveOrZero
    val earnRateBp: Int? = null,
    @field:jakarta.validation.constraints.Positive
    val expiryDays: Int? = null,
)

/** 포인트 만료 처리 결과(관리자) */
data class PointExpireResponse(
    val expiredTotal: Long,
)
