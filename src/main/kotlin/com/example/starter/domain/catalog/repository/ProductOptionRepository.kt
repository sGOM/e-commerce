package com.example.starter.domain.catalog.repository

import com.example.starter.domain.catalog.entity.ProductOption
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ProductOptionRepository : JpaRepository<ProductOption, Long> {

    /** 옵션 + 상품(판매상태/가격) + 재고를 함께 로딩 — 장바구니 담기 검증용 */
    @EntityGraph(attributePaths = ["product", "inventory"])
    fun findWithProductAndInventoryById(id: Long): Optional<ProductOption>

    /** 옵션 + 상품을 함께 로딩(배치) — 리뷰 작성/작성대기 목록에서 상품 id·이름 조회용 */
    @EntityGraph(attributePaths = ["product"])
    fun findWithProductByIdIn(ids: Collection<Long>): List<ProductOption>

    /** 옵션 + 상품 + 판매자를 함께 로딩(배치) — 타임딜 응답에 상품명/상점명을 노출할 때 사용 */
    @EntityGraph(attributePaths = ["product", "product.seller"])
    fun findWithProductAndSellerByIdIn(ids: Collection<Long>): List<ProductOption>

    /** SKU 로 옵션 + 상품 + 판매자 + 재고 로딩(배치) — 판매자 재고 일괄 수정용 */
    @EntityGraph(attributePaths = ["product", "product.seller", "inventory"])
    fun findBySkuIn(skus: Collection<String>): List<ProductOption>
}
