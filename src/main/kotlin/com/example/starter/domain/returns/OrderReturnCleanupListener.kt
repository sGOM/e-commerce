package com.example.starter.domain.returns

import com.example.starter.domain.order.event.OrderCanceledEvent
import com.example.starter.domain.returns.entity.ReturnStatus
import com.example.starter.domain.returns.repository.OrderReturnRepository
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 반품 진행 중에 주문 전체가 환불·취소되면(관리자 환불·PG 웹훅) 열린 반품을 CANCELED 로 닫는다.
 * 주문 취소 트랜잭션 안에서 동기로 돌아 함께 커밋·롤백된다. 두지 않으면 판매자 화면에 처리할 수 없는 요청이 남는다.
 */
@Component
class OrderReturnCleanupListener(
    private val orderReturnRepository: OrderReturnRepository,
) {

    @EventListener
    fun on(event: OrderCanceledEvent) {
        orderReturnRepository.findBySubOrderOrderIdAndStatusIn(event.orderId, OPEN).forEach { it.cancelByOrderRefund() }
    }

    companion object {
        private val OPEN = ReturnStatus.entries.filter { it.isOpen }
    }
}
