package com.example.starter.domain.catalog

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.dto.PopularProductResponse
import com.example.starter.domain.catalog.dto.ProductDetailResponse
import com.example.starter.domain.catalog.dto.ProductSearchCondition
import com.example.starter.domain.catalog.dto.ProductSort
import com.example.starter.domain.catalog.dto.ProductSummaryResponse
import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.CategoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.seller.entity.Seller
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 고객용 상품 조회. 검색은 **Kotlin JDSL** 동적 쿼리(조건 null 이면 where 절 자동 제외)로 구성하며,
 * 고객에게는 노출 상태([ProductStatus.VISIBLE])의 상품만 보여준다.
 */
@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
) {

    fun search(condition: ProductSearchCondition, pageable: Pageable): PageResponse<ProductSummaryResponse> {
        val categoryIds = condition.categoryId?.let { categoryWithDescendants(it) }
        val page = productRepository.findPage(pageable) {
            select(entity(Product::class))
                .from(entity(Product::class))
                .whereAnd(
                    path(Product::status).`in`(ProductStatus.VISIBLE),
                    condition.keyword?.let { path(Product::name).like("%$it%") },
                    categoryIds?.let { path(Product::category).path(Category::id).`in`(it) },
                    condition.sellerId?.let { path(Product::seller).path(Seller::id).eq(it) },
                    condition.minPrice?.let { path(Product::basePrice).ge(it) },
                    condition.maxPrice?.let { path(Product::basePrice).le(it) },
                )
                .orderBy(
                    when (condition.sort) {
                        ProductSort.LATEST -> null
                        ProductSort.PRICE_ASC -> path(Product::basePrice).asc()
                        ProductSort.PRICE_DESC -> path(Product::basePrice).desc()
                        ProductSort.RATING_DESC -> path(Product::avgRating).desc()
                    },
                    path(Product::id).desc(),
                )
        }
        // 단일 엔티티 조회라 결과 원소는 null 이 아니다.
        return PageResponse.of(page) { ProductSummaryResponse.from(it!!) }
    }

    /** [rootId] 와 그 아래 모든 하위 카테고리 id. */
    // ponytail: 카테고리 전체를 읽어 메모리 BFS, 카테고리가 수천 개를 넘으면 재귀 CTE 로 전환
    private fun categoryWithDescendants(rootId: Long): Set<Long> {
        val childrenByParent = categoryRepository.findAll().groupBy { it.parent?.id }
        val ids = mutableSetOf(rootId)
        val queue = ArrayDeque(listOf(rootId))
        while (queue.isNotEmpty()) {
            childrenByParent[queue.removeFirst()].orEmpty().forEach { child ->
                val childId = requireNotNull(child.id)
                if (ids.add(childId)) queue.add(childId)
            }
        }
        return ids
    }

    /**
     * 인기 상품 — 누적 판매 수량 상위 [limit] 개를 랭킹 순서로 반환한다.
     * 집계 후 현재 노출 상태([ProductStatus.isVisible])인 상품만 남긴다(품절은 노출, 숨김/삭제 제외).
     */
    fun getPopular(limit: Int): List<PopularProductResponse> {
        val rows = productRepository.findPopularProductRows(limit)
        if (rows.isEmpty()) return emptyList()
        val soldById = rows.associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
        val productById = productRepository.findAllById(soldById.keys).associateBy { it.id }
        // 집계가 매긴 판매 순서를 유지하며 노출 상품만 매핑
        return soldById.keys.mapNotNull { id ->
            val product = productById[id] ?: return@mapNotNull null
            if (!product.status.isVisible) return@mapNotNull null
            PopularProductResponse.from(product, soldById[id] ?: 0)
        }
    }

    fun getDetail(id: Long): ProductDetailResponse {
        val product = productRepository.findWithDetailById(id)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }
        // 미노출 상태(DRAFT/HIDDEN)는 고객에게 존재하지 않는 것으로 취급
        if (!product.status.isVisible) {
            throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
        }
        return ProductDetailResponse.from(product)
    }
}
