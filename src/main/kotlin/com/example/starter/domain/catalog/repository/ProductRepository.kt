package com.example.starter.domain.catalog.repository

import com.example.starter.domain.catalog.entity.Product
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

/**
 * 상품 조회. 검색은 [KotlinJdslJpqlExecutor] 동적 쿼리로,
 * 상세는 옵션·재고까지 한 번에 로딩(N+1 방지)한다.
 */
interface ProductRepository : JpaRepository<Product, Long>, KotlinJdslJpqlExecutor {

    /** 상세 조회 — 옵션과 옵션별 재고를 함께 로딩 */
    @EntityGraph(attributePaths = ["seller", "category", "options", "options.inventory"])
    fun findWithDetailById(id: Long): Optional<Product>
}
