package com.example.starter.domain.promotion

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.dto.ProductSummaryResponse
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.promotion.dto.AdminCollectionSearchCondition
import com.example.starter.domain.promotion.dto.ChangeCollectionStatusRequest
import com.example.starter.domain.promotion.dto.CollectionDetailResponse
import com.example.starter.domain.promotion.dto.CollectionProductResponse
import com.example.starter.domain.promotion.dto.CollectionRequest
import com.example.starter.domain.promotion.dto.CollectionSummaryResponse
import com.example.starter.domain.promotion.dto.ReplaceCollectionProductsRequest
import com.example.starter.domain.promotion.entity.Collection
import com.example.starter.domain.promotion.repository.CollectionRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 관리자(MD) 컬렉션 편성. 이번 범위는 관리자 전용이며(셀러 신청은 Out of scope), 상품은 참조만 하고
 * 진열 순서·노출기간·활성화만 관리한다.
 */
@Service
@Transactional(readOnly = true)
class AdminCollectionService(
    private val collectionRepository: CollectionRepository,
    private val productRepository: ProductRepository,
) {

    fun search(condition: AdminCollectionSearchCondition, pageable: Pageable): PageResponse<CollectionSummaryResponse> {
        val page = collectionRepository.findPage(pageable) {
            select(entity(Collection::class))
                .from(entity(Collection::class))
                .whereAnd(
                    condition.status?.let { path(Collection::status).eq(it) },
                )
                .orderBy(path(Collection::displayOrder).asc(), path(Collection::id).desc())
        }
        return PageResponse.of(page) { CollectionSummaryResponse.from(it!!) }
    }

    fun getDetail(id: Long): CollectionDetailResponse = toDetail(findWithProducts(id))

    @Transactional
    fun create(request: CollectionRequest): CollectionDetailResponse {
        validatePeriod(request.startAt!!, request.endAt!!)
        val collection = collectionRepository.save(
            Collection(
                title = request.title!!,
                subtitle = request.subtitle,
                bannerImageUrl = request.bannerImageUrl,
                startAt = request.startAt,
                endAt = request.endAt,
                displayOrder = request.displayOrder,
            ),
        )
        return toDetail(collection)
    }

    @Transactional
    fun update(id: Long, request: CollectionRequest): CollectionDetailResponse {
        validatePeriod(request.startAt!!, request.endAt!!)
        val collection = findWithProducts(id)
        collection.updateMeta(
            title = request.title!!,
            subtitle = request.subtitle,
            bannerImageUrl = request.bannerImageUrl,
            startAt = request.startAt,
            endAt = request.endAt,
            displayOrder = request.displayOrder,
        )
        return toDetail(collection)
    }

    /** 상태 전이(DRAFT/PUBLISHED/ENDED, AC2). 빈 컬렉션도 PUBLISHED 를 허용한다(오픈 이슈 #2). */
    @Transactional
    fun changeStatus(id: Long, request: ChangeCollectionStatusRequest): CollectionDetailResponse {
        val collection = findWithProducts(id)
        collection.changeStatus(request.status!!)
        return toDetail(collection)
    }

    /** 편성 상품 전체 교체(목록+순서 일괄 저장, AC1/AC4). */
    @Transactional
    fun replaceProducts(id: Long, request: ReplaceCollectionProductsRequest): CollectionDetailResponse {
        val collection = findWithProducts(id)
        val productIds = request.productIds
        if (productIds.distinct().size != productIds.size) {
            throw BusinessException(ErrorCode.COLLECTION_DUPLICATE_PRODUCT)
        }
        if (productIds.isNotEmpty()) {
            val foundCount = productRepository.findAllById(productIds).size
            if (foundCount != productIds.distinct().size) {
                throw BusinessException(ErrorCode.PRODUCT_NOT_FOUND)
            }
        }
        collection.replaceProducts(productIds)
        return toDetail(collection)
    }

    private fun findWithProducts(id: Long): Collection =
        collectionRepository.findWithProductsById(id).orElseThrow { BusinessException(ErrorCode.COLLECTION_NOT_FOUND) }

    /** 관리자 화면은 판매 상태와 무관하게 편성된 상품을 모두 보여준다(고객 노출 필터는 [CollectionService] 참고). */
    private fun toDetail(collection: Collection): CollectionDetailResponse {
        val productIds = collection.products.map { it.productId }
        val productById = if (productIds.isEmpty()) emptyMap() else productRepository.findAllById(productIds).associateBy { it.id }
        val items = collection.products
            .sortedBy { it.displayOrder }
            .mapNotNull { cp ->
                val product = productById[cp.productId] ?: return@mapNotNull null
                CollectionProductResponse(displayOrder = cp.displayOrder, product = ProductSummaryResponse.from(product))
            }
        return CollectionDetailResponse.from(collection, items)
    }

    private fun validatePeriod(startAt: Instant, endAt: Instant) {
        if (!endAt.isAfter(startAt)) {
            throw BusinessException(ErrorCode.COLLECTION_INVALID_PERIOD)
        }
    }
}
