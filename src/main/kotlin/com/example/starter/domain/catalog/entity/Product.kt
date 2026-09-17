package com.example.starter.domain.catalog.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.domain.seller.entity.Seller
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * 상품. 마켓플레이스에서 반드시 한 [Seller] 에 속한다.
 *
 * 가격은 원(KRW) 단위 정수([basePrice])로 보관한다. 실제 판매가는 옵션의
 * [ProductOption.additionalPrice] 를 더한 값이며, 재고는 옵션 단위([ProductOption])로 관리한다.
 */
@Entity
@Table(name = "products")
class Product(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: Seller,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(name = "base_price", nullable = false)
    var basePrice: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    var category: Category? = null,

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.DRAFT,

    // 새벽배송 가능 상품 여부(`docs/planning/delivery-slot.md` AC3). 실무적으로는 셀러(물류 계약) 단위가
    // 더 합리적이지만(§9 오픈이슈 #2), 문서 초안대로 상품 단위 필드로 확정했다 — 셀러 단위 계약은 향후
    // Seller 엔티티에 계약 플래그를 추가하고 상품 등록 시 상속시키는 방식으로 후속 확장 가능.
    @Column(name = "dawn_delivery_eligible", nullable = false)
    var dawnDeliveryEligible: Boolean = false,

    /** 대표 이미지 URL(`/api/uploads/...` 또는 외부 URL). */
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    val options: MutableList<ProductOption> = mutableListOf()

    // 평점 요약(비정규화) — 조회 트래픽이 리뷰 변경보다 훨씬 잦아 매 조회 집계 대신 컬럼으로 캐시한다.
    // 리뷰 작성/수정/삭제/숨김·복원 시 [com.example.starter.domain.review.ReviewService] 가 재계산해 갱신한다.
    @Column(name = "avg_rating", nullable = false, precision = 2, scale = 1)
    var avgRating: BigDecimal = BigDecimal.ZERO

    @Column(name = "review_count", nullable = false)
    var reviewCount: Int = 0

    /** 옵션을 추가하고 양방향 연관관계를 맞춘다. */
    fun addOption(option: ProductOption) {
        options.add(option)
        option.product = this
    }

    /** 리뷰 집계 결과로 평점 요약을 갱신한다(노출/숨김 제외 리뷰 기준은 호출측에서 계산). */
    fun updateReviewSummary(avgRating: BigDecimal, reviewCount: Int) {
        this.avgRating = avgRating
        this.reviewCount = reviewCount
    }
}
