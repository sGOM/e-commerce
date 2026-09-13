package com.example.starter.domain.promotion.repository

import com.example.starter.domain.promotion.entity.Collection
import com.example.starter.domain.promotion.entity.CollectionStatus
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.Optional

/**
 * 컬렉션 조회. 관리자 검색(상태 조건)은 [KotlinJdslJpqlExecutor] 동적 쿼리로, 고객 공개 목록은
 * 상태+기간 파생 쿼리로 처리한다.
 */
interface CollectionRepository : JpaRepository<Collection, Long>, KotlinJdslJpqlExecutor {

    /** 상세 조회 — 편성 상품까지 함께 로딩(N+1 방지). */
    @EntityGraph(attributePaths = ["products"])
    fun findWithProductsById(id: Long): Optional<Collection>

    /**
     * 고객 노출용 목록 — [status] 이고 [at] 이 노출기간(startAt~endAt) 내인 컬렉션을 [displayOrder] 순으로 반환한다(AC5, AC9).
     */
    fun findByStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByDisplayOrderAscIdAsc(
        status: CollectionStatus,
        at: Instant,
        atAgain: Instant,
    ): List<Collection>
}
