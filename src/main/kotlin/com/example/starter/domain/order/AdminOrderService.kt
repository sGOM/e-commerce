package com.example.starter.domain.order

import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.order.dto.OrderResponse
import com.example.starter.domain.order.dto.OrderSummaryResponse
import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 관리자 주문 운영. 전체 주문을 상태/기간으로 검색(Kotlin JDSL 동적 쿼리)하고 환불을 처리한다.
 */
@Service
@Transactional(readOnly = true)
class AdminOrderService(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
) {

    fun search(
        status: OrderStatus?,
        from: Instant?,
        to: Instant?,
        pageable: Pageable,
    ): PageResponse<OrderSummaryResponse> {
        val page = orderRepository.findPage(pageable) {
            select(entity(Order::class))
                .from(entity(Order::class))
                .whereAnd(
                    status?.let { path(Order::status).eq(it) },
                    from?.let { path(Order::createdAt).ge(it) },
                    to?.let { path(Order::createdAt).le(it) },
                )
                .orderBy(path(Order::id).desc())
        }
        return PageResponse.of(page) { OrderSummaryResponse.from(it!!) }
    }

    @Transactional
    fun refund(orderId: Long): OrderResponse = orderService.refundByAdmin(orderId)
}
