package com.example.starter.domain.catalog.repository

import com.example.starter.domain.catalog.entity.ProductOption
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ProductOptionRepository : JpaRepository<ProductOption, Long> {

    /** 옵션 + 상품(판매상태/가격) + 재고를 함께 로딩 — 장바구니 담기 검증용 */
    @EntityGraph(attributePaths = ["product", "inventory"])
    fun findWithProductAndInventoryById(id: Long): Optional<ProductOption>
}
