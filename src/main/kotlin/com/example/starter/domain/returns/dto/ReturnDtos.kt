package com.example.starter.domain.returns.dto

import com.example.starter.domain.returns.entity.OrderReturn
import com.example.starter.domain.returns.entity.ReturnReason
import com.example.starter.domain.returns.entity.ReturnStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/** 반품 요청(회원). 금액은 서버가 정책으로 계산한다. */
data class CreateReturnRequest(
    @field:NotNull
    val subOrderId: Long? = null,
    @field:NotNull
    val reason: ReturnReason? = null,
    @field:Size(max = 500)
    val detail: String? = null,
)

data class ReturnResponse(
    val returnId: Long,
    val subOrderId: Long,
    val orderNumber: String,
    val reason: ReturnReason,
    val detail: String?,
    val status: ReturnStatus,
    val returnFee: Long,
    val refundAmount: Long,
    val createdAt: Instant,
) {
    companion object {
        fun from(request: OrderReturn) = ReturnResponse(
            returnId = requireNotNull(request.id),
            subOrderId = requireNotNull(request.subOrder.id),
            orderNumber = request.subOrder.order.orderNumber,
            reason = request.reason,
            detail = request.detail,
            status = request.status,
            returnFee = request.returnFee,
            refundAmount = request.refundAmount,
            createdAt = request.createdAt,
        )
    }
}
