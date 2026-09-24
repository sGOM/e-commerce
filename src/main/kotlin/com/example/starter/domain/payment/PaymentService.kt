package com.example.starter.domain.payment

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.payment.dto.PaymentResponse
import com.example.starter.domain.payment.entity.Payment
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.gateway.PaymentApproveCommand
import com.example.starter.domain.payment.gateway.PaymentCancelCommand
import com.example.starter.domain.payment.gateway.PaymentGateway
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.point.PointService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제. Mock PG 로 주문([Order]) 단위 1건을 승인하고, 성공 시 주문/하위주문을 PAID 로 전이한다.
 *
 * 멱등성: order_id UNIQUE + 이미 PAID 면 재승인 없이 기존 결제를 그대로 반환해 **중복 결제를 막는다**.
 * 결제 금액은 주문의 [Order.payableAmount](서버 계산)를 신뢰한다.
 */
@Service
@Transactional(readOnly = true)
class PaymentService(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val pointService: PointService,
) {

    @Transactional
    fun pay(userId: Long, orderId: Long, paymentKey: String? = null): PaymentResponse =
        pay(orderRepository.findByIdAndUserId(orderId, userId).orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }, paymentKey)

    /**
     * 비회원 결제 — 주문번호 + 주문 시 연락처로 본인 확인. 회원 계정에 연결(claim)된 주문은 회원 결제만 허용하며,
     * 존재 여부를 흘리지 않도록 불일치와 같은 404 로 응답한다.
     */
    @Transactional
    fun payGuest(orderNumber: String, ordererPhone: String, paymentKey: String? = null): PaymentResponse =
        pay(
            orderRepository.findByOrderNumberAndOrdererPhone(orderNumber, ordererPhone)
                .filter { it.userId == null }
                .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) },
            paymentKey,
        )

    private fun pay(order: Order, paymentKey: String?): PaymentResponse {
        val orderId = requireNotNull(order.id)

        // 멱등: 이미 결제 완료면 재승인 없이 기존 결과 반환(중복 결제 방지)
        val existing = paymentRepository.findByOrderId(orderId).orElse(null)
        if (existing != null && existing.status == PaymentStatus.PAID) {
            return PaymentResponse.from(existing)
        }
        if (order.status != OrderStatus.CREATED) {
            throw BusinessException(ErrorCode.ORDER_NOT_PAYABLE)
        }

        val payment = existing ?: Payment(orderId = orderId, amount = order.payableAmount)
        val result = paymentGateway.approve(
            PaymentApproveCommand(order.orderNumber, order.payableAmount, paymentKey),
        )
        if (!result.success) {
            // MVP: 거절은 402로 반환하고 트랜잭션을 롤백한다(주문은 CREATED 유지 → 재시도 가능).
            // 실패 이력의 영속 보존은 실 PG 연동 시 별도 트랜잭션(REQUIRES_NEW)으로 도입한다.
            throw BusinessException(ErrorCode.PAYMENT_FAILED, result.message)
        }

        payment.markPaid(result.transactionId)
        order.status = OrderStatus.PAID
        order.subOrders.forEach { it.status = SubOrderStatus.PAID }
        paymentRepository.save(payment)

        // 결제 확정 → 정책 적립률만큼 포인트 적립(회원 주문 한정)
        order.userId?.let { pointService.earn(it, order.payableAmount, orderId) }

        return PaymentResponse.from(payment)
    }

    /**
     * 결제 환불. PG 에 [amount] 만큼 취소를 요청하고, 성공하면 [fullyCanceled] 에 따라 결제를 취소 상태로 두거나
     * 부분 환불 이력만 남긴다. PG 가 거절하면 예외로 호출자 트랜잭션 전체(재고·쿠폰 복원 포함)를 롤백한다.
     *
     * 호출자는 내부 상태 변경을 모두 끝낸 **마지막 단계**에서 부른다 — 외부 호출 뒤에 DB 작업이 실패하면 PG 만
     * 취소된 채 남기 때문이다. 그래도 커밋 자체가 실패할 수 있어 [idempotencyKey] 를 결정적으로 만들어, 재시도가
     * PG 에서 한 번만 처리되게 한다.
     */
    @Transactional
    fun refund(orderId: Long, amount: Long, reason: String, idempotencyKey: String, fullyCanceled: Boolean) {
        val payment = paymentRepository.findByOrderId(orderId).orElse(null) ?: return
        if (amount > 0) {
            val result = paymentGateway.cancel(PaymentCancelCommand(payment.pgTransactionId, amount, reason, idempotencyKey))
            if (!result.success) {
                throw BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, result.message)
            }
        }
        if (fullyCanceled) payment.markCanceled(reason) else payment.recordPartialRefund(amount)
    }
}
