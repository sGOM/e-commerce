package com.example.starter.domain.promotion.dto

import com.example.starter.domain.catalog.dto.ProductSummaryResponse
import com.example.starter.domain.promotion.entity.Collection
import com.example.starter.domain.promotion.entity.CollectionStatus
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant

/** 컬렉션 생성/수정(메타) 요청 — 전체 교체(AC1). [endAt] > [startAt] 은 서비스에서 검증한다. */
data class CollectionRequest(
    @field:NotEmpty @field:Size(max = 200)
    val title: String?,
    @field:Size(max = 300)
    val subtitle: String? = null,
    @field:Size(max = 500)
    val bannerImageUrl: String? = null,
    @field:NotNull
    val startAt: Instant?,
    @field:NotNull
    val endAt: Instant?,
    @field:PositiveOrZero
    val displayOrder: Int = 0,
)

/** 컬렉션 상태 변경 요청(AC2). */
data class ChangeCollectionStatusRequest(
    @field:NotNull
    val status: CollectionStatus?,
)

/**
 * 컬렉션 상품 편성(목록+순서 일괄 저장, AC1/AC4). 목록 순서가 곧 노출 순서다. 빈 목록도 허용한다
 * (오픈 이슈 #2 — 상품 0개인 채로 PUBLISHED 전환은 허용하되 고객 화면에서만 숨김 처리).
 */
data class ReplaceCollectionProductsRequest(
    val productIds: List<Long> = emptyList(),
)

/** 관리자 컬렉션 검색 조건. */
data class AdminCollectionSearchCondition(
    val status: CollectionStatus? = null,
)

/** 컬렉션 목록 항목(고객/관리자 공용 요약). */
data class CollectionSummaryResponse(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val bannerImageUrl: String?,
    val startAt: Instant,
    val endAt: Instant,
    val status: CollectionStatus,
    val displayOrder: Int,
    val productCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(collection: Collection) = CollectionSummaryResponse(
            id = requireNotNull(collection.id),
            title = collection.title,
            subtitle = collection.subtitle,
            bannerImageUrl = collection.bannerImageUrl,
            startAt = collection.startAt,
            endAt = collection.endAt,
            status = collection.status,
            displayOrder = collection.displayOrder,
            productCount = collection.products.size,
            createdAt = collection.createdAt,
            updatedAt = collection.updatedAt,
        )
    }
}

/** 컬렉션에 편성된 상품(순서 포함). */
data class CollectionProductResponse(
    val displayOrder: Int,
    val product: ProductSummaryResponse,
)

/** 컬렉션 상세(메타 + 편성 상품 목록). */
data class CollectionDetailResponse(
    val id: Long,
    val title: String,
    val subtitle: String?,
    val bannerImageUrl: String?,
    val startAt: Instant,
    val endAt: Instant,
    val status: CollectionStatus,
    val displayOrder: Int,
    val products: List<CollectionProductResponse>,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(collection: Collection, products: List<CollectionProductResponse>) = CollectionDetailResponse(
            id = requireNotNull(collection.id),
            title = collection.title,
            subtitle = collection.subtitle,
            bannerImageUrl = collection.bannerImageUrl,
            startAt = collection.startAt,
            endAt = collection.endAt,
            status = collection.status,
            displayOrder = collection.displayOrder,
            products = products,
            createdAt = collection.createdAt,
            updatedAt = collection.updatedAt,
        )
    }
}
