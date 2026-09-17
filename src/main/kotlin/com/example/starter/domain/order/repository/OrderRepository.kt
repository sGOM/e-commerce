package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.Order
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface OrderRepository : JpaRepository<Order, Long>, KotlinJdslJpqlExecutor {

    /** 내 주문 목록(헤더만). 상세 컬렉션은 상세 조회에서 트랜잭션 내 지연 로딩한다. */
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<Order>

    /** 게스트 주문 조회(주문번호 + 연락처 검증과 함께 사용) */
    fun findByOrderNumber(orderNumber: String): Optional<Order>

    /** 주문번호 + 주문 시 연락처가 모두 일치하는 주문(게스트 조회/연결/결제의 본인 확인) */
    fun findByOrderNumberAndOrdererPhone(orderNumber: String, ordererPhone: String): Optional<Order>

    /**
     * 순구매액(실결제액, 취소/환불 제외) 집계 — 로열티 등급 재계산 배치용
     * (`LoyaltyTierBatchService`). [com.example.starter.domain.catalog.repository.ProductRepository.findPopularProductRows]
     * 와 같은 유효 판매 상태(PAID/PREPARING/SHIPPED/DELIVERED, CREATED·CANCELED 제외)의 하위 주문
     * 결제기여액([com.example.starter.domain.order.entity.SubOrder.payableShare])을 회원별로 합산한다.
     * 배송비([SubOrder.deliveryFee])가 포함된 금액이지만, 등급 산정 목적상 상품가와 분리할 실익이
     * 낮아 단순화했다(추후 필요 시 재검토).
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT o.user_id, SUM(so.payable_share)
            FROM sub_orders so
            JOIN orders o ON o.id = so.order_id
            WHERE o.user_id IS NOT NULL
              AND so.status IN ('PAID','PREPARING','SHIPPED','DELIVERED')
              AND so.created_at >= :since
            GROUP BY o.user_id
        """,
    )
    fun aggregateNetPurchaseAmountSince(@Param("since") since: Instant): List<Array<Any>>
}
