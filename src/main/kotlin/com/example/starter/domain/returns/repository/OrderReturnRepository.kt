package com.example.starter.domain.returns.repository

import com.example.starter.domain.returns.entity.OrderReturn
import com.example.starter.domain.returns.entity.ReturnStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OrderReturnRepository : JpaRepository<OrderReturn, Long> {

    @EntityGraph(attributePaths = ["subOrder", "subOrder.order"])
    fun findByUserIdOrderByIdDesc(userId: Long): List<OrderReturn>

    /** 판매자 본인 판매분 반품만(소유권 격리). */
    @EntityGraph(attributePaths = ["subOrder", "subOrder.order"])
    fun findBySubOrderSellerIdOrderByIdDesc(sellerId: Long): List<OrderReturn>

    fun findBySubOrderId(subOrderId: Long): List<OrderReturn>

    /** 주문이 통째로 취소될 때 닫아야 할 진행 중 반품. */
    fun findBySubOrderOrderIdAndStatusIn(orderId: Long, statuses: Collection<ReturnStatus>): List<OrderReturn>

    @EntityGraph(attributePaths = ["subOrder", "subOrder.order", "subOrder.items"])
    fun findByIdAndSubOrderSellerId(id: Long, sellerId: Long): Optional<OrderReturn>
}
