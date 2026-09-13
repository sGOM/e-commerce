package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface SubOrderRepository : JpaRepository<SubOrder, Long> {

    /** 판매자 본인 판매분만 조회(소유권 격리). 발송 처리용으로 주문/항목/배송을 함께 로딩. */
    @EntityGraph(attributePaths = ["order", "items", "shipment"])
    fun findWithDetailsByIdAndSellerId(id: Long, sellerId: Long): Optional<SubOrder>

    @EntityGraph(attributePaths = ["order", "items", "shipment"])
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<SubOrder>

    @EntityGraph(attributePaths = ["order", "items", "shipment"])
    fun findBySellerIdAndStatusOrderByIdDesc(sellerId: Long, status: SubOrderStatus): List<SubOrder>

    /** 미정산(settlementId IS NULL) 이면서 정산 대상 상태인 하위 주문 — 정산 생성용 */
    @EntityGraph(attributePaths = ["seller"])
    fun findBySettlementIdIsNullAndStatusIn(statuses: Collection<SubOrderStatus>): List<SubOrder>
}
