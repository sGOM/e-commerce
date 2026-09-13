package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.OrderItem
import com.example.starter.domain.order.entity.SubOrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OrderItemRepository : JpaRepository<OrderItem, Long> {

    /** 리뷰 작성 자격 검증용 — 하위 주문·주문(소유자) 을 함께 로딩한다. */
    @EntityGraph(attributePaths = ["subOrder", "subOrder.order"])
    fun findWithSubOrderById(id: Long): Optional<OrderItem>

    /** 회원의 리뷰 작성 대기 목록 후보 — 배송완료(DELIVERED) 하위 주문의 항목만. */
    @EntityGraph(attributePaths = ["subOrder", "subOrder.order"])
    fun findBySubOrder_Order_UserIdAndSubOrder_StatusOrderByIdDesc(
        userId: Long,
        status: SubOrderStatus,
    ): List<OrderItem>
}
