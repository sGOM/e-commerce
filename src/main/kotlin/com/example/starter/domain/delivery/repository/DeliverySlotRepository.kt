package com.example.starter.domain.delivery.repository

import com.example.starter.domain.delivery.entity.DeliverySlot
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.time.LocalDate

/**
 * 배송 슬롯 조회/동시성 제어. 정원(reservedCount)은 재고([com.example.starter.domain.catalog.repository.InventoryRepository])
 * ·타임딜([com.example.starter.domain.flashsale.repository.FlashSaleRepository]) 과 동일한 패턴의
 * 단일 원자적 UPDATE 로 관리한다.
 */
interface DeliverySlotRepository : JpaRepository<DeliverySlot, Long>, KotlinJdslJpqlExecutor {

    /**
     * 슬롯 예약(정원 1 증가). `reservedCount < capacity` 이고 아직 마감 전(`cutoffAt > now`)일 때만
     * 증가시키며, 영향 행이 0이면 정원 초과 또는 마감(AC7)으로 판단한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE DeliverySlot s
           SET s.reservedCount = s.reservedCount + 1
         WHERE s.id = :id
           AND s.reservedCount < s.capacity
           AND s.cutoffAt > :now
        """,
    )
    fun reserve(@Param("id") id: Long, @Param("now") now: Instant): Int

    /** 슬롯 예약 복원(취소). 마감/정원 상태와 무관하게 복원한다(AC9). 음수 방지를 위해 0 초과일 때만 적용. */
    @Modifying(flushAutomatically = true)
    @Query(
        """
        UPDATE DeliverySlot s
           SET s.reservedCount = s.reservedCount - 1
         WHERE s.id = :id
           AND s.reservedCount > 0
        """,
    )
    fun release(@Param("id") id: Long): Int

    /**
     * 공개 목록(GET /api/delivery-slots) — 해당 날짜에 아직 마감 전·정원 여유가 있는 슬롯만,
     * 전국 공통 슬롯이거나 배송지 우편번호가 [regionScope] 로 시작하는 슬롯만 반환한다(AC2/AC4/AC8).
     * 새벽배송 지역 화이트리스트([com.example.starter.domain.delivery.entity.DeliveryRegion]) 필터는
     * 조회 후 서비스 계층에서 적용한다.
     */
    @Query(
        """
        SELECT s FROM DeliverySlot s
         WHERE s.slotDate = :date
           AND s.cutoffAt > :now
           AND s.reservedCount < s.capacity
           AND (s.regionScope IS NULL OR (:postalCode IS NOT NULL AND :postalCode LIKE CONCAT(s.regionScope, '%')))
         ORDER BY s.type ASC, s.startTime ASC
        """,
    )
    fun findAvailable(
        @Param("date") date: LocalDate,
        @Param("postalCode") postalCode: String?,
        @Param("now") now: Instant,
    ): List<DeliverySlot>
}
