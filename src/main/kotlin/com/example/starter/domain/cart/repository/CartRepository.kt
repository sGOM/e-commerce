package com.example.starter.domain.cart.repository

import com.example.starter.domain.cart.entity.Cart
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface CartRepository : JpaRepository<Cart, Long> {

    fun findByUserId(userId: Long): Optional<Cart>

    /** 장바구니 + 항목 + 옵션/상품/재고를 한 번에 로딩 (조회 시 N+1 방지) */
    @EntityGraph(attributePaths = ["items", "items.option", "items.option.product", "items.option.inventory"])
    fun findWithItemsByUserId(userId: Long): Optional<Cart>

    /**
     * 장바구니 이탈 리마인드 배치 대상 조회 — 항목이 비어있지 않고, 마지막 활동이 [threshold] 이전
     * (=N시간 이상 경과)이며, 아직 이번 이탈 구간에 대해 리마인드를 보내지 않은(중복 발송 방지)
     * 장바구니만 조회한다(`CartReminderBatchService`).
     */
    @Query(
        """
        SELECT c FROM Cart c
         WHERE SIZE(c.items) > 0
           AND c.lastActivityAt < :threshold
           AND (c.lastReminderAt IS NULL OR c.lastReminderAt < c.lastActivityAt)
        """,
    )
    fun findAbandonedCarts(@Param("threshold") threshold: Instant): List<Cart>
}
