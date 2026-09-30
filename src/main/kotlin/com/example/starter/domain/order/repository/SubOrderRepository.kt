package com.example.starter.domain.order.repository

import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

interface SubOrderRepository : JpaRepository<SubOrder, Long> {

    /** 회원 주문 중 결제 후 아직 끝나지 않은(PAID/PREPARING/SHIPPED/RETURNING) 하위 주문이 있는지 — 탈퇴 가능 여부. */
    @Query("select count(s) > 0 from SubOrder s where s.order.userId = :userId and s.status in :statuses")
    fun existsByUserIdAndStatusIn(
        @Param("userId") userId: Long,
        @Param("statuses") statuses: Collection<SubOrderStatus>,
    ): Boolean

    /** 판매자 본인 판매분만 조회(소유권 격리). 발송 처리용으로 주문/항목/배송을 함께 로딩. */
    @EntityGraph(attributePaths = ["order", "items", "shipment"])
    fun findWithDetailsByIdAndSellerId(id: Long, sellerId: Long): Optional<SubOrder>

    @EntityGraph(attributePaths = ["order", "items", "shipment"])
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<SubOrder>

    @EntityGraph(attributePaths = ["order", "items", "shipment"])
    fun findBySellerIdAndStatusOrderByIdDesc(sellerId: Long, status: SubOrderStatus): List<SubOrder>

    /**
     * 미정산(settlementId IS NULL) 이면서 정산 대상 상태인 하위 주문 — 정산 생성용. 행을 잠가(SELECT … FOR UPDATE)
     * 같은 행을 잠그는 반품 요청([findWithLockById])과 순서를 정한다. 잠금 대기 뒤 PostgreSQL 이 조건을 다시 확인하므로
     * 그 사이 반품 진행(RETURNING)으로 바뀐 행은 빠지고, 정산이 반품 상태를 옛 값으로 덮어쓰지 않는다.
     * (외부 조인에는 FOR UPDATE 를 걸 수 없어 판매자 페치 그래프는 쓰지 않는다 — 판매자는 id·프록시만 쓴다.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findBySettlementIdIsNullAndStatusIn(statuses: Collection<SubOrderStatus>): List<SubOrder>

    /** 행 잠금 조회 — 같은 하위 주문의 반품 요청 동시 진입·정산 생성과의 경합을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: Long): Optional<SubOrder>

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

    /**
     * 관리자 대시보드 — [from, to) 의 한국 시간 일자별 주문 수(주문 단위 중복 제거)와 판매액.
     * 결과 행: [일자(java.sql.Date), 주문 수, 판매액]. 주문이 없는 날은 행이 없다.
     */
    @Query(
        value = """
        select cast(s.created_at at time zone 'Asia/Seoul' as date) as day,
               count(distinct s.order_id), coalesce(sum(s.subtotal), 0)
        from sub_orders s
        where s.status in (:statuses) and s.created_at >= :from and s.created_at < :to
        group by day order by day
        """,
        nativeQuery = true,
    )
    fun dailySales(
        @Param("statuses") statuses: Collection<String>,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<Array<Any>>

    interface SalesSummary {
        val orderCount: Long
        val salesAmount: Long
    }
}
