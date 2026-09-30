package com.example.starter.domain.order.event

/**
 * 주문 전체가 취소·환불됐다(관리자 환불·PG 웹훅·미결제/선물 만료 — OrderService.doCancel). 같은 트랜잭션 안에서
 * 발행되므로 리스너가 함께 정리하면 원자적으로 반영된다(예: 진행 중 반품 닫기).
 */
data class OrderCanceledEvent(val orderId: Long)
