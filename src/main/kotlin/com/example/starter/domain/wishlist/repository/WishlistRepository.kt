package com.example.starter.domain.wishlist.repository

import com.example.starter.domain.wishlist.entity.Wishlist
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface WishlistRepository : JpaRepository<Wishlist, Long> {

    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    /** 본인 위시 항목만 삭제 가능 — 소유권 검증(AC1). */
    fun findByUserIdAndProductId(userId: Long, productId: Long): Optional<Wishlist>

    /** 회원당 위시리스트 최대 개수 상한 검사용(§4). */
    fun countByUserId(userId: Long): Long

    fun findByUserIdOrderByIdDesc(userId: Long, pageable: Pageable): Page<Wishlist>

    /**
     * 가격이 baseline 보다 낮아진(=인하된) 항목만 조회한다(마이페이지 `priceDropOnly` 필터, AC5).
     * `Wishlist` 는 [com.example.starter.domain.catalog.entity.Product] 와 연관관계를 맺지 않는 관례
     * (도메인 간 참조는 순수 id) 때문에 네이티브 쿼리로 `products` 테이블과 조인한다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT w.* FROM wishlists w
            JOIN products p ON p.id = w.product_id
            WHERE w.user_id = :userId AND w.baseline_price > p.base_price
            ORDER BY w.id DESC
        """,
        countQuery = """
            SELECT COUNT(*) FROM wishlists w
            JOIN products p ON p.id = w.product_id
            WHERE w.user_id = :userId AND w.baseline_price > p.base_price
        """,
    )
    fun findPriceDroppedByUserId(@Param("userId") userId: Long, pageable: Pageable): Page<Wishlist>

    /**
     * 상품의 위시 항목 전체를 행 잠금과 함께 조회한다(가격 변경 이벤트 동시 처리 경쟁 방지 —
     * [com.example.starter.domain.restock.repository.RestockAlertRepository.findPendingForUpdate] 와
     * 동일 원칙).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wishlist w WHERE w.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): List<Wishlist>

    /** 셀러 상품 상세 "찜 N명" 카운트(AC13). */
    fun countByProductId(productId: Long): Long
}
