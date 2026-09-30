package com.example.starter.domain.returns

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.delivery.ShippingPolicyService
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.returns.dto.CreateReturnRequest
import com.example.starter.domain.returns.dto.ReturnResponse
import com.example.starter.domain.returns.entity.OrderReturn
import com.example.starter.domain.returns.repository.OrderReturnRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 회원 반품 요청(ROADMAP 1.5). 하위 주문(판매자) 단위로, 발송(SHIPPED) 이후 또는 구매확정 후 정책 일수 이내만 받는다.
 * 이미 정산된 하위 주문은 정산 역분개가 없어 받지 않는다(2026-09-30 정책 — 관리자 환불로 처리).
 */
@Service
@Transactional(readOnly = true)
class ReturnService(
    private val orderReturnRepository: OrderReturnRepository,
    private val subOrderRepository: SubOrderRepository,
    private val shippingPolicyService: ShippingPolicyService,
) {

    fun getMyReturns(userId: Long): List<ReturnResponse> =
        orderReturnRepository.findByUserIdOrderByIdDesc(userId).map { ReturnResponse.from(it) }

    @Transactional
    fun request(userId: Long, request: CreateReturnRequest): ReturnResponse {
        // 행을 잠가 같은 하위 주문의 동시 요청(두 번째는 RETURNING 을 보고 409)·정산 생성과 순서를 정한다
        val subOrder = subOrderRepository.findWithLockById(request.subOrderId!!)
            .orElseThrow { BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) }
        if (subOrder.order.userId != userId) {
            throw BusinessException(ErrorCode.SUB_ORDER_NOT_FOUND) // 본인 주문이 아니면 존재를 숨긴다
        }
        if (subOrder.status != SubOrderStatus.SHIPPED && subOrder.status != SubOrderStatus.DELIVERED) {
            throw BusinessException(ErrorCode.SUB_ORDER_NOT_RETURNABLE)
        }
        val deliveredAt = subOrder.deliveredAt
        val window = Duration.ofDays(shippingPolicyService.returnWindowDays().toLong())
        if (subOrder.status == SubOrderStatus.DELIVERED && deliveredAt != null && Instant.now().isAfter(deliveredAt.plus(window))) {
            throw BusinessException(ErrorCode.RETURN_WINDOW_EXPIRED)
        }
        if (subOrder.settlementId != null) {
            throw BusinessException(ErrorCode.RETURN_ALREADY_SETTLED)
        }

        val reason = request.reason!!
        val returnFee = if (reason.buyerPaysFee) shippingPolicyService.returnFee() else 0
        val saved = orderReturnRepository.save(
            OrderReturn(
                subOrder = subOrder,
                userId = userId,
                reason = reason,
                detail = request.detail?.takeIf { it.isNotBlank() },
                previousSubOrderStatus = subOrder.status,
                returnFee = returnFee,
                refundAmount = (subOrder.payableShare - returnFee).coerceAtLeast(0),
            ),
        )
        subOrder.status = SubOrderStatus.RETURNING // 정산 대상(SettlementService.SETTLEABLE)에서 빠진다
        return ReturnResponse.from(saved)
    }
}
