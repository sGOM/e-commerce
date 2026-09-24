package com.example.starter.domain.order

import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * 미결제 주문 자동 만료(ROADMAP 1.6). 결제창 이탈·결제 실패로 CREATED 에 남은 주문이 재고 예약을 계속 점유하지 않도록
 * [ttl](기본 30분 — 토스는 인증 후 10분 안에 승인해야 하므로 진행 중인 결제를 끊지 않는 여유) 이 지난 주문을 취소한다.
 * 건별 처리는 [OrderService.expireUnpaidOrder](REQUIRES_NEW + 행 잠금)에 맡겨 한 건의 실패가 다른 건을 막지 않는다.
 */
@Service
class UnpaidOrderExpiryService(
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    @Value("\${order.unpaid-expiry.ttl:PT30M}") private val ttl: Duration,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun expireUnpaid(now: Instant = Instant.now()): Int {
        val cutoff = now.minus(ttl)
        var expired = 0
        orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.CREATED, cutoff).mapNotNull { it.id }.forEach { id ->
            try {
                if (orderService.expireUnpaidOrder(id, cutoff)) expired++
            } catch (ex: Exception) {
                log.error("미결제 주문 만료 실패(orderId={}) — 이 건만 건너뛰고 계속 진행", id, ex)
            }
        }
        log.info("미결제 주문 만료 완료: {}건", expired)
        return expired
    }
}
