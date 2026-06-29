package com.example.starter.domain.catalog

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.dto.ProductDetailResponse
import com.example.starter.domain.catalog.dto.ProductSearchCondition
import com.example.starter.domain.catalog.dto.ProductSummaryResponse
import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductStatus
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
) {

    fun search(condition: ProductSearchCondition, pageable: Pageable): PageResponse<ProductSummaryResponse> {
        val page = productRepository.findPage(pageable) {
            select(entity(Product::class))
                .from(entity(Product::class))
                .whereAnd(
                    path(Product::status).`in`(ProductStatus.VISIBLE),
                    condition.keyword?.let { path(Product::name).like("%$it%") },
                    condition.categoryId?.let { path(Product::category).path(Category::id).eq(it) },
                    condition.sellerId?.let { path(Product::seller).path(Seller::id).eq(it) },
                )
                .orderBy(path(Product::id).desc())
        }
        // 단일 엔티티 조회라 결과 원소는 null 이 아니다.
        return PageResponse.of(page) { ProductSummaryResponse.from(it!!) }
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
