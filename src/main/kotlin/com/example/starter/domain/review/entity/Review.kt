package com.example.starter.domain.review.entity

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

/**
 * 상품 리뷰. [orderItemId] 는 구매 인증 근거(`OrderItem`, unique — 항목당 리뷰 1개)이며,
 * [productId]/[userId] 는 조회 최적화를 위한 비정규화 참조다(다른 애그리거트 참조는 이 코드베이스
 * 관례대로 연관관계 대신 순수 id 로 둔다 — [com.example.starter.domain.order.entity.OrderItem.optionId] 참고).
 *
 * [authorName] 은 작성 시점 스냅샷(회원정보 변경과 무관, 목록 조회 시 users 조인 방지).
 */
@Entity
@Table(name = "reviews")
class Review(
    @Column(name = "order_item_id", nullable = false, unique = true)
    val orderItemId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "author_name", nullable = false, length = 50)
    val authorName: String,

    @Column(nullable = false)
    var rating: Int,

    @Column(columnDefinition = "text", nullable = false)
    var content: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 아래 필드들은 엔티티 내부 메서드(hide/restore/report/replaceImages)로만 변경한다(관례상 setter 비공개는
    // allOpen(프록시 상속) 과 충돌해 컴파일 에러가 나므로 public var 로 두되 호출측은 메서드를 통해서만 접근한다).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReviewStatus = ReviewStatus.VISIBLE

    @Column(name = "report_count", nullable = false)
    var reportCount: Int = 0

    // 포토리뷰 필터(photoOnly) 를 이미지 조인 없이 처리하기 위한 비정규화 플래그.
    @Column(name = "has_photo", nullable = false)
    var hasPhoto: Boolean = false

    @OneToMany(mappedBy = "review", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    val images: MutableList<ReviewImage> = mutableListOf()

    /** 평점/본문/이미지를 함께 수정한다(전체 교체). */
    fun update(rating: Int, content: String, imageUrls: List<String>) {
        this.rating = rating
        this.content = content
        replaceImages(imageUrls)
    }

    fun replaceImages(imageUrls: List<String>) {
        images.clear()
        imageUrls.forEachIndexed { index, url ->
            val image = ReviewImage(imageUrl = url, displayOrder = index)
            image.review = this
            images.add(image)
        }
        hasPhoto = images.isNotEmpty()
    }

    /** 관리자 숨김 처리(AC13). */
    fun hide() {
        status = ReviewStatus.HIDDEN
    }

    /** 관리자 복구(숨김 해제) — 정상 노출로 되돌린다. */
    fun restore() {
        status = ReviewStatus.VISIBLE
    }

    /**
     * 신고 누적. 임계치([threshold]) 도달 시 자동으로 REPORTED 로 전이한다(자동 숨김 아님, AC12).
     * 이미 HIDDEN 이면 상태를 바꾸지 않는다(관리자 처리 우선).
     */
    fun report(threshold: Int) {
        reportCount += 1
        if (status == ReviewStatus.VISIBLE && reportCount >= threshold) {
            status = ReviewStatus.REPORTED
        }
    }
}
