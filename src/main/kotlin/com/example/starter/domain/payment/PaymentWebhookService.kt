package com.example.starter.domain.payment

import com.example.starter.domain.order.OrderService
import com.example.starter.domain.payment.gateway.PaymentGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** 토스 웹훅 본문. 서명이 없는 이벤트라 [Data.paymentKey] 외에는 쓰지 않는다. */
data class TossWebhookRequest(
    val eventType: String? = null,
    val data: Data? = null,
) {
    data class Data(val paymentKey: String? = null)
}

/**
 * 결제 웹훅 처리(ROADMAP 1.4). 토스 PAYMENT_STATUS_CHANGED 는 서명이 없으므로 payload 의 상태는 믿지 않고
 * paymentKey 로 PG 를 재조회한 결과만 반영한다 — 위조 요청은 PG 에 존재하는 실제 상태 외에는 아무것도 바꿀 수 없다.
 *
 * 반영 대상은 "PG 에서는 전액 취소인데 우리는 PAID"(대시보드 취소, 취소 후 커밋 실패 등)뿐이다. 외부 부분 취소는
 * 어느 SubOrder 몫인지 알 수 없어 로그만 남긴다. https://docs.tosspayments.com/reference/using-api/webhook-events
 */
@Service
class PaymentWebhookService(
    private val paymentGateway: PaymentGateway,
    private val orderService: OrderService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handleToss(request: TossWebhookRequest) {
        if (request.eventType != "PAYMENT_STATUS_CHANGED") return
        val paymentKey = request.data?.paymentKey ?: return
        val pg = paymentGateway.lookup(paymentKey) ?: return

        when (pg.status) {
            "CANCELED" -> orderService.cancelByPg(pg.orderNumber)
            "PARTIAL_CANCELED" -> log.warn("PG 부분 취소 웹훅 — 수동 확인 필요(orderNumber={}, paymentKey={})", pg.orderNumber, paymentKey)
            else -> {}
        }
    }
}
