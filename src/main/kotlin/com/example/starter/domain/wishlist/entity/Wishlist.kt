package com.example.starter.domain.wishlist.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 위시리스트(찜) 항목. Product 단위(옵션 단위 아님, `docs/planning/wishlist-price-alert.md` §4).
 *
 * [userId]/[productId] 는 다른 애그리거트 참조를 순수 id 로 두는 이 코드베이스 관례를 따른다
 * ([com.example.starter.domain.restock.entity.RestockAlert] 참고).
 */
@Entity
@Table(name = "wishlists")
class Wishlist(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    // 담을 당시 또는 마지막 알림 발송 시점의 Product.basePrice 스냅샷(§4 baseline 정의)
    @Column(name = "baseline_price", nullable = false)
    var baselinePrice: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "last_notified_at")
    var lastNotifiedAt: Instant? = null
        protected set

    /**
     * 가격 인하 알림 발송 완료 처리. baseline 을 발송 시점 가격으로 갱신해 다음 인하도 다시 감지할 수
     * 있게 한다(재입고 알림과 달리 "소멸성 1회"가 아닌 반복 이벤트, §4 알림 재발송 원칙).
     */
    fun markNotified(newBaselinePrice: Long, at: Instant) {
        baselinePrice = newBaselinePrice
        lastNotifiedAt = at
    }
}
