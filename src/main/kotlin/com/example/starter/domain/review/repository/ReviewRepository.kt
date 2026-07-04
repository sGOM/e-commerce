package com.example.starter.domain.review.repository

import com.example.starter.domain.review.entity.Review
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * 검색은 [KotlinJdslJpqlExecutor] 동적 쿼리(고객 상품별 목록 정렬/필터, 관리자 검색)로 처리한다.
 */
interface ReviewRepository : JpaRepository<Review, Long>, KotlinJdslJpqlExecutor {

    fun existsByOrderItemId(orderItemId: Long): Boolean

    fun findByOrderItemId(orderItemId: Long): Optional<Review>

    /** 본인 리뷰만 수정/삭제 가능 — 소유권 검증(AC6). 남의 리뷰는 404 로 존재를 숨긴다. */
    fun findByIdAndUserId(id: Long, userId: Long): Optional<Review>

    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<Review>

    /**
     * 상품 평점 요약 재계산 — HIDDEN 은 집계에서 제외(AC10). 리뷰가 없으면 (0, 0) 한 행을 반환.
     * 다중 스칼라 프로젝션은 Hibernate 6 에서 `List<Array<Any>>`(행별 Object[]) 로 매핑되므로,
     * 서비스에서 `.first()` 로 `[avgRating(Double), reviewCount(Long)]` 행을 꺼내 BigDecimal 로 변환한다.
     */
    @Query(
        """
        SELECT COALESCE(AVG(r.rating), 0), COUNT(r)
        FROM Review r
        WHERE r.productId = :productId AND r.status <> com.example.starter.domain.review.entity.ReviewStatus.HIDDEN
        """,
    )
    fun aggregateVisibleByProductId(@Param("productId") productId: Long): List<Array<Any>>
}
