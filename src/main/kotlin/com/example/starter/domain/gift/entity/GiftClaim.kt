package com.example.starter.domain.gift.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 선물 주문의 공유 링크(토큰). 1개 주문당 1행([orderId] UNIQUE) — 멀티셀러 선물 주문이라도 수령자는
 * 한 명이라 SubOrder 별 개별 링크를 두지 않는다(`docs/planning/gift-order.md` §4).
 *
 * [orderId] 는 다른 애그리거트 참조를 순수 id 로 두는 코드베이스 관례를 따른다
 * ([com.example.starter.domain.review.entity.Review.userId] 참고) — order 도메인이 gift 도메인을
 * 몰라도 되게 하기 위함이며, 반대로 gift 도메인은 [com.example.starter.domain.order.repository.OrderRepository]
 * 로 주문을 직접 조회한다.
 *
 * [token] 은 회원가입 없이(비회원 수령자, §4 게스트 선물) 접근 가능한 유일한 식별자라 URL 에 노출된다
 * — UUID 기반으로 추측 불가능한 무작위값을 쓴다.
 */
@Entity
@Table(name = "gift_claims")
class GiftClaim(
    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: Long,

    @Column(nullable = false, unique = true, length = 64)
    val token: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: GiftClaimStatus = GiftClaimStatus.PENDING
        protected set

    @Column(name = "claimed_at")
    var claimedAt: Instant? = null
        protected set

    val isExpired: Boolean
        get() = status == GiftClaimStatus.PENDING && Instant.now().isAfter(expiresAt)

    /** 수령자 수락(배송지 입력 완료). PENDING 이고 기한 내여야 한다(AC7/AC8). */
    fun claim(now: Instant = Instant.now()) {
        check(status == GiftClaimStatus.PENDING) { "이미 처리된 선물 링크입니다." }
        check(!now.isAfter(expiresAt)) { "만료된 선물 링크입니다." }
        status = GiftClaimStatus.CLAIMED
        claimedAt = now
    }

    /** 미수락 만료 배치 처리(AC9). PENDING 상태에서만 의미가 있다. */
    fun expire() {
        if (status == GiftClaimStatus.PENDING) {
            status = GiftClaimStatus.EXPIRED
        }
    }

    /** 구매자가 수락 전 취소(AC11). PENDING 상태에서만 의미가 있다(이미 CLAIMED 면 그대로 둔다). */
    fun cancel() {
        if (status == GiftClaimStatus.PENDING) {
            status = GiftClaimStatus.CANCELED
        }
    }
}
