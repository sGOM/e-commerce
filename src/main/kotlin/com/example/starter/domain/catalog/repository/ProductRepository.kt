package com.example.starter.domain.catalog.repository

import com.example.starter.domain.catalog.entity.Product
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

/**
 * 상품 조회. 검색은 [KotlinJdslJpqlExecutor] 동적 쿼리로,
 * 상세는 옵션·재고까지 한 번에 로딩(N+1 방지)한다.
 */
interface ProductRepository : JpaRepository<Product, Long>, KotlinJdslJpqlExecutor {

    /** 상세 조회 — 옵션과 옵션별 재고를 함께 로딩 */
    @EntityGraph(attributePaths = ["seller", "category", "options", "options.inventory"])
    fun findWithDetailById(id: Long): Optional<Product>

    /** 판매자 본인 상품 전체(상태 무관) — 옵션·재고 함께 로딩 */
    @EntityGraph(attributePaths = ["category", "options", "options.inventory"])
    fun findBySellerIdOrderByIdDesc(sellerId: Long): List<Product>

    fun existsByCategoryId(categoryId: Long): Boolean

    /**
     * 인기 상품 집계 — 결제 완료 이후(취소 제외) 하위 주문의 판매 수량을 상품 단위로 합산해
     * 많이 팔린 순으로 (product_id, 판매수량) 을 반환한다. 노출 상태 필터는 서비스에서 적용한다.
     */
    @Query(
        nativeQuery = true,
        value = """
            SELECT po.product_id, SUM(oi.quantity)
            FROM order_items oi
            JOIN product_options po ON po.id = oi.option_id
            JOIN sub_orders so ON so.id = oi.sub_order_id
            WHERE so.status IN ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED')
            GROUP BY po.product_id
            ORDER BY SUM(oi.quantity) DESC
            LIMIT :limit
        """,
    )
    fun findPopularProductRows(@Param("limit") limit: Int): List<Array<Any>>
}
