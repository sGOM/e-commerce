package com.example.starter.domain.returns.repository

import com.example.starter.domain.returns.entity.OrderReturn
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface OrderReturnRepository : JpaRepository<OrderReturn, Long> {

    @EntityGraph(attributePaths = ["subOrder", "subOrder.order"])
    fun findByUserIdOrderByIdDesc(userId: Long): List<OrderReturn>

    /** 판매자 본인 판매분 반품만(소유권 격리). */
    @EntityGraph(attributePaths = ["subOrder", "subOrder.order"])
    fun findBySubOrderSellerIdOrderByIdDesc(sellerId: Long): List<OrderReturn>

    @EntityGraph(attributePaths = ["subOrder", "subOrder.order", "subOrder.items"])
    fun findByIdAndSubOrderSellerId(id: Long, sellerId: Long): Optional<OrderReturn>
}
