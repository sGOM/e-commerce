package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.OrderStatus
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface OrderRepository : JpaRepository<Order, Long>, KotlinJdslJpqlExecutor {

    /** 내 주문 목록(헤더만). 상세 컬렉션은 상세 조회에서 트랜잭션 내 지연 로딩한다. */
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>

    fun findByIdAndUserId(id: Long, userId: Long): Optional<Order>

    fun findByUserIdAndStatus(userId: Long, status: OrderStatus): List<Order>

    /** 게스트 주문 조회(주문번호 + 연락처 검증과 함께 사용) */
    fun findByOrderNumber(orderNumber: String): Optional<Order>

    /** 주문번호 + 주문 시 연락처가 모두 일치하는 주문(게스트 조회/연결/결제의 본인 확인) */
    fun findByOrderNumberAndOrdererPhone(orderNumber: String, ordererPhone: String): Optional<Order>

    /** 결제 기한이 지난 미결제 주문 후보(자동 만료 배치). 처리 직전에 [findWithLockById] 로 다시 확인한다. */
    fun findByStatusAndCreatedAtBefore(status: OrderStatus, before: Instant): List<Order>

    /** 행 잠금(SELECT ... FOR UPDATE) 조회 — 결제와 미결제 만료가 같은 주문을 동시에 바꾸지 못하게 한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): Optional<Order>

    /**
     * 순구매액(실결제액, 취소/환불 제외) 집계 — 로열티 등급 재계산 배치용
     * (`LoyaltyTierBatchService`). [com.example.starter.domain.catalog.repository.ProductRepository.findPopularProductRows]
     * 와 같은 유효 판매 상태(PAID/PREPARING/SHIPPED/DELIVERED, CREATED·CANCELED 제외)의 하위 주문
     * 결제기여액([com.example.starter.domain.order.entity.SubOrder.payableShare])을 회원별로 합산한다.
     * 기본 배송비 도입(ROADMAP 7.1) 이후로는 배송비([SubOrder.deliveryFee])를 빼고 상품 결제액만 합산한다
     * — 판매자 수가 많은 주문일수록 등급이 오르는 왜곡을 막는다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT o.user_id, SUM(so.payable_share - so.delivery_fee)
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
