package com.example.starter.domain.flashsale.repository

import com.example.starter.domain.flashsale.entity.FlashSale
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.Optional

/**
 * 타임딜 조회/동시성 제어. 한도 수량은 재고([com.example.starter.domain.catalog.repository.InventoryRepository])
 * 와 동일한 패턴의 단일 원자적 UPDATE 로 관리한다.
 */
interface FlashSaleRepository : JpaRepository<FlashSale, Long>, KotlinJdslJpqlExecutor {

    /**
     * 한도 예약(증가). `soldQuantity + qty <= limitQuantity` 조건을 만족할 때만 증가시키며,
     * 영향 행이 0이면 한도 소진(또는 그 사이 관리자가 강제 종료/기간 만료)으로 판단한다(AC6).
     *
     * 재고 예약과 마찬가지로 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로 flush 를 강제한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE FlashSale f
           SET f.soldQuantity = f.soldQuantity + :qty
         WHERE f.id = :id
           AND f.status = com.example.starter.domain.flashsale.entity.FlashSaleStatus.ACTIVE
           AND f.startAt <= :now
           AND f.endAt > :now
           AND f.soldQuantity + :qty <= f.limitQuantity
        """,
    )
    fun reserve(@Param("id") id: Long, @Param("qty") qty: Int, @Param("now") now: Instant): Int

    /**
     * 한도 복원(취소). 딜이 이미 종료/강제종료 되었어도 복원은 수행한다(AC7, 통계 정확성 목적).
     * 음수 방지를 위해 soldQuantity >= qty 일 때만 적용.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE FlashSale f
           SET f.soldQuantity = f.soldQuantity - :qty
         WHERE f.id = :id
           AND f.soldQuantity >= :qty
        """,
    )
    fun release(@Param("id") id: Long, @Param("qty") qty: Int): Int

    /** 지금 이 순간 이 옵션에 적용 가능한 진행 중 딜(있으면 최대 1건, AC2 로 시간 중복은 미리 방지됨). */
    @Query(
        """
        SELECT f FROM FlashSale f
         WHERE f.productOptionId = :optionId
           AND f.status = com.example.starter.domain.flashsale.entity.FlashSaleStatus.ACTIVE
           AND f.startAt <= :now
           AND f.endAt > :now
           AND f.soldQuantity < f.limitQuantity
        """,
    )
    fun findOngoingByOptionId(@Param("optionId") optionId: Long, @Param("now") now: Instant): Optional<FlashSale>

    /** 공개 목록 — 진행 중인 모든 딜을 종료 임박순으로 반환한다(AC3). */
    @Query(
        """
        SELECT f FROM FlashSale f
         WHERE f.status = com.example.starter.domain.flashsale.entity.FlashSaleStatus.ACTIVE
           AND f.startAt <= :now
           AND f.endAt > :now
           AND f.soldQuantity < f.limitQuantity
         ORDER BY f.endAt ASC
        """,
    )
    fun findOngoing(@Param("now") now: Instant): List<FlashSale>

    /** 등록 시(AC2) 같은 옵션에 시간이 겹치는(강제종료 제외) 딜이 있는지 검사한다. */
    @Query(
        """
        SELECT COUNT(f) FROM FlashSale f
         WHERE f.productOptionId = :optionId
           AND f.status = com.example.starter.domain.flashsale.entity.FlashSaleStatus.ACTIVE
           AND f.startAt < :endAt
           AND f.endAt > :startAt
        """,
    )
    fun countOverlapping(
        @Param("optionId") optionId: Long,
        @Param("startAt") startAt: Instant,
        @Param("endAt") endAt: Instant,
    ): Long

    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<FlashSale>
}
