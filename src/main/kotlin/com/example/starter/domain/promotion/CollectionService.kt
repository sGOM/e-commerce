package com.example.starter.domain.promotion

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.catalog.dto.ProductSummaryResponse
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.promotion.dto.CollectionDetailResponse
import com.example.starter.domain.promotion.dto.CollectionProductResponse
import com.example.starter.domain.promotion.dto.CollectionSummaryResponse
import com.example.starter.domain.promotion.entity.Collection
import com.example.starter.domain.promotion.entity.CollectionStatus
import com.example.starter.domain.promotion.repository.CollectionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 고객용 컬렉션 조회(공개, 인증 불필요). 진행 중(`PUBLISHED` and 기간 내) 컬렉션만 노출하고(AC5),
 * 편성 상품 중 판매중지(`HIDDEN`)된 상품은 조회 시점에 제외한다(AC7, 편성 자체는 유지).
 */
@Service
@Transactional(readOnly = true)
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val productRepository: ProductRepository,
) {

    /** 진행 중 컬렉션 목록(displayOrder 순, AC9). 상품이 0개인 컬렉션은 고객 화면에서 숨긴다(오픈 이슈 #2). */
    fun listActive(): List<CollectionSummaryResponse> {
        val now = Instant.now()
        return collectionRepository
            .findByStatusAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByDisplayOrderAscIdAsc(
                CollectionStatus.PUBLISHED,
                now,
                now,
            )
            .filter { it.products.isNotEmpty() }
            .map { CollectionSummaryResponse.from(it) }
    }

    /** 컬렉션 상세 + 편성 상품(지정된 순서, AC6). 진행 중이 아니면 존재하지 않는 것으로 취급한다. */
    fun getDetail(id: Long): CollectionDetailResponse {
        val now = Instant.now()
        val collection = collectionRepository.findWithProductsById(id)
            .orElseThrow { BusinessException(ErrorCode.COLLECTION_NOT_FOUND) }
        if (!collection.isActiveAt(now)) {
            throw BusinessException(ErrorCode.COLLECTION_NOT_FOUND)
        }
        return CollectionDetailResponse.from(collection, visibleProducts(collection))
    }

    /** 노출 상태([com.example.starter.domain.catalog.entity.ProductStatus.isVisible])인 상품만 편성 순서 유지하며 매핑한다(AC7, AC8). */
    private fun visibleProducts(collection: Collection): List<CollectionProductResponse> {
        val productIds = collection.products.map { it.productId }
        if (productIds.isEmpty()) return emptyList()
        val productById = productRepository.findAllById(productIds).associateBy { it.id }
        return collection.products
            .sortedBy { it.displayOrder }
            .mapNotNull { cp ->
                val product = productById[cp.productId] ?: return@mapNotNull null
                if (!product.status.isVisible) return@mapNotNull null
                CollectionProductResponse(displayOrder = cp.displayOrder, product = ProductSummaryResponse.from(product))
            }
    }
}
