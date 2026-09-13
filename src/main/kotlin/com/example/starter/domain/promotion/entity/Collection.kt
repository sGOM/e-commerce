package com.example.starter.domain.promotion.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant

/**
 * 기획전/컬렉션. MD(관리자)가 상품을 수동으로 편집 진열하는 마케팅 묶음이다(카테고리와 달리
 * 분류 체계가 아니라 노출 목적 묶음). [Product] 는 [CollectionProduct.productId] 로 참조만 하고
 * 수정하지 않는다(이 코드베이스 관례상 다른 애그리거트 참조는 연관관계 대신 순수 id — Review 참고).
 *
 * 이번 범위는 관리자 전용 편성이다(셀러 신청 플로우는 Out of scope, 기획서 §8).
 */
@Entity
@Table(name = "collections")
class Collection(
    @Column(nullable = false, length = 200)
    var title: String,

    @Column(length = 300)
    var subtitle: String? = null,

    @Column(name = "banner_image_url", length = 500)
    var bannerImageUrl: String? = null,

    @Column(name = "start_at", nullable = false)
    var startAt: Instant,

    @Column(name = "end_at", nullable = false)
    var endAt: Instant,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 관례상 setter 비공개는 allOpen(프록시 상속) 과 충돌해 컴파일 에러가 나므로 public var 로 두되
    // 호출측은 changeStatus 를 통해서만 접근한다(Review.status 참고).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: CollectionStatus = CollectionStatus.DRAFT

    @OneToMany(mappedBy = "collection", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    val products: MutableList<CollectionProduct> = mutableListOf()

    /** 메타(제목/부제/배너/노출기간/우선순위) 를 함께 수정한다(AC1, PUT 전체 교체). */
    fun updateMeta(
        title: String,
        subtitle: String?,
        bannerImageUrl: String?,
        startAt: Instant,
        endAt: Instant,
        displayOrder: Int,
    ) {
        this.title = title
        this.subtitle = subtitle
        this.bannerImageUrl = bannerImageUrl
        this.startAt = startAt
        this.endAt = endAt
        this.displayOrder = displayOrder
    }

    /**
     * 상태 전이(DRAFT/PUBLISHED/ENDED). 빈 컬렉션(상품 0개) 도 PUBLISHED 전환을 허용한다 — 운영
     * 편의상 전환은 허용하되 고객 화면 노출만 막는다(오픈 이슈 #2, [products] 가 비어있으면
     * 공개 목록 조회 시 서비스 계층에서 제외).
     */
    fun changeStatus(status: CollectionStatus) {
        this.status = status
    }

    /**
     * 편성 상품을 전체 교체한다(목록+순서 일괄 저장, AC1/AC4). [productIds] 순서가 곧 [CollectionProduct.displayOrder]다.
     */
    fun replaceProducts(productIds: List<Long>) {
        products.clear()
        productIds.forEachIndexed { index, productId ->
            val collectionProduct = CollectionProduct(productId = productId, displayOrder = index)
            collectionProduct.collection = this
            products.add(collectionProduct)
        }
    }

    /** 고객에게 지금 노출해야 하는지 — PUBLISHED 이고 [at] 이 노출기간 내인 경우만(AC3, AC5). */
    fun isActiveAt(at: Instant): Boolean =
        status == CollectionStatus.PUBLISHED && !at.isBefore(startAt) && !at.isAfter(endAt)
}
