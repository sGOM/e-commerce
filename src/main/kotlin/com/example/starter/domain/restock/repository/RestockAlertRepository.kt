package com.example.starter.domain.restock.repository

import com.example.starter.domain.restock.entity.RestockAlert
import com.example.starter.domain.restock.entity.RestockAlertStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface RestockAlertRepository : JpaRepository<RestockAlert, Long> {

    fun existsByUserIdAndOptionIdAndStatus(userId: Long, optionId: Long, status: RestockAlertStatus): Boolean

    /** 본인 신청만 취소 가능 — 소유권 검증(AC4). */
    fun findByUserIdAndOptionIdAndStatus(userId: Long, optionId: Long, status: RestockAlertStatus): Optional<RestockAlert>

    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<RestockAlert>

    /**
     * 옵션의 PENDING 신청 전체를 행 잠금(PESSIMISTIC_WRITE)과 함께 조회한다.
     *
     * 같은 옵션에 대한 재고 조정이 짧은 간격으로 여러 번 발생하면(0→3→0→5) 이벤트가 여러 번 발행될 수
     * 있다(오픈 이슈 3). 이 쿼리로 행을 잠그면, 동시에 실행된 두 번째 처리는 첫 번째가 커밋할 때까지
     * 대기했다가 재조회하므로 이미 NOTIFIED 로 바뀐 행은 다시 걸리지 않는다 — 즉 "이전 값 조회 후 상태
     * 전이"를 원자적으로 보장해 1인 1회 발송(AC6)을 지킨다. `InventoryRepository` 의 원자적 UPDATE와
     * 달리 발송 대상 사용자 목록이 필요해 SELECT ... FOR UPDATE 방식을 택했다.
     * 참고: https://www.postgresql.org/docs/current/explicit-locking.html#LOCKING-ROWS
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT r FROM RestockAlert r
         WHERE r.optionId = :optionId
           AND r.status = com.example.starter.domain.restock.entity.RestockAlertStatus.PENDING
        """,
    )
    fun findPendingForUpdate(@Param("optionId") optionId: Long): List<RestockAlert>
}
