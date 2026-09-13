package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
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

    /** 판매자 대시보드 — [from, to) 에 생성된 하위 주문 중 해당 상태의 건수와 판매액 */
    @Query(
        """
        select count(s) as orderCount, coalesce(sum(s.subtotal), 0L) as salesAmount
        from SubOrder s
        where s.seller.id = :sellerId and s.status in :statuses and s.createdAt >= :from and s.createdAt < :to
        """,
    )
    fun summarizeSales(
        @Param("sellerId") sellerId: Long,
        @Param("statuses") statuses: Collection<SubOrderStatus>,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): SalesSummary

    /** 판매자 대시보드 — 아직 정산서에 묶이지 않은 판매액 */
    @Query(
        """
        select coalesce(sum(s.subtotal), 0L) from SubOrder s
        where s.seller.id = :sellerId and s.settlementId is null and s.status in :statuses
        """,
    )
    fun sumUnsettledSubtotal(
        @Param("sellerId") sellerId: Long,
        @Param("statuses") statuses: Collection<SubOrderStatus>,
    ): Long

    interface SalesSummary {
        val orderCount: Long
        val salesAmount: Long
    }
}
