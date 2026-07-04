package com.example.starter.domain.subscription.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.domain.order.entity.ShippingAddress
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 정기배송 구독(`docs/planning/subscription-delivery.md`). 상품 옵션 1개 + 수량을 주기(일 단위)로
 * 자동 재주문한다. 여러 상품을 묶은 정기 박스는 범위 밖(§8) — 옵션 1개당 구독 1행이다.
 *
 * 주문자/배송지는 [Order][com.example.starter.domain.order.entity.Order] 와 동일하게 스냅샷 컬럼으로
 * 보관한다(저장된 배송지부(Address book) 도메인이 아직 없어 기획서 §5의 `addressId(FK)` 대신 임베디드
 * 값 객체를 그대로 재사용 — 배송지 변경은 해지 후 재등록으로 처리, 후속 과제).
 *
 * "조회 시점 판정" 원칙([com.example.starter.domain.membership.entity.Membership] 과 동일): 배치가
 * 지연되어도 [nextOrderAt] 은 항상 "직전 예정일 + 주기"로만 전진하므로(현재 시각 기준이 아님) 배치
 * 지연이 다음 회차 앞당김/밀림으로 누적되지 않는다(자기 보정).
 */
@Entity
@Table(name = "delivery_subscriptions")
class DeliverySubscription(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "option_id", nullable = false)
    val optionId: Long,

    @Column(nullable = false)
    var quantity: Int,

    @Column(name = "cycle_days", nullable = false)
    var cycleDays: Int,

    @Column(name = "orderer_name", nullable = false, length = 100)
    var ordererName: String,

    @Column(name = "orderer_phone", nullable = false, length = 30)
    var ordererPhone: String,

    @Column(name = "orderer_email", nullable = false, length = 255)
    var ordererEmail: String,

    @Embedded
    var shippingAddress: ShippingAddress,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DeliverySubscriptionStatus = DeliverySubscriptionStatus.ACTIVE

    @Column(name = "next_order_at", nullable = false)
    var nextOrderAt: Instant = Instant.now()

    // 다음 회차 1회 건너뛰기 요청(AC5). 배치가 처리하며 소모 후 false 로 되돌린다.
    @Column(name = "skip_requested", nullable = false)
    var skipRequested: Boolean = false

    // 연속 결제실패 횟수. 정책 임계치 도달 시 자동 PAUSED(AC8). 결제/재고 성공 시 0으로 리셋.
    @Column(name = "consecutive_failure_count", nullable = false)
    var consecutiveFailureCount: Int = 0

    @Column(name = "canceled_at")
    var canceledAt: Instant? = null

    /** 직전 예정일 기준으로 다음 회차를 계산한다(현재 시각 기준이 아님 — 배치 지연에 자기 보정). */
    fun advanceCycle() {
        nextOrderAt = nextOrderAt.plusSeconds(cycleDays * SECONDS_PER_DAY)
    }

    /** 회원이 요청한 스킵(AC5) 소모 처리 — 다음 회차로 전진하고 요청 플래그를 되돌린다. */
    fun consumeUserSkip() {
        advanceCycle()
        skipRequested = false
    }

    /** 주문 생성(결제 포함) 성공 — 다음 회차로 전진하고 연속 실패 카운트를 리셋한다. */
    fun recordOrderCreated() {
        advanceCycle()
        consecutiveFailureCount = 0
    }

    /** 재고 부족/일시 품절로 이번 회차 스킵 — 카드와 무관한 사유라 연속 실패 카운트는 건드리지 않는다. */
    fun recordSkippedOutOfStock() {
        advanceCycle()
    }

    /** 결제 실패로 이번 회차 스킵 — 연속 실패 카운트 증가(임계치 도달 여부는 정책 기준으로 서비스가 판단). */
    fun recordPaymentFailure() {
        advanceCycle()
        consecutiveFailureCount += 1
    }

    /** 연속 결제실패 임계치 도달/상품 판매중지 — 시스템에 의한 자동 일시정지(§4/AC8). */
    fun autoPause() {
        status = DeliverySubscriptionStatus.PAUSED
    }

    /** 회원 요청 일시정지(AC3). ACTIVE 상태에서만 허용 — 검증은 서비스 계층에서 수행한다. */
    fun pauseByUser() {
        status = DeliverySubscriptionStatus.PAUSED
    }

    /**
     * 재개(AC3 "재개 시 다음 청구일부터 재개"). 일시정지 기간 동안 [nextOrderAt] 이 이미 과거로
     * 지나버렸다면(장기간 정지) 재개 즉시 밀린 회차가 한꺼번에 청구되지 않도록 "재개 시점 + 주기"로
     * 다음 회차를 다시 잡는다 — 예정대로 아직 도래 전이면 원래 일정을 그대로 유지한다.
     */
    fun resume(now: Instant) {
        status = DeliverySubscriptionStatus.ACTIVE
        if (nextOrderAt.isBefore(now)) {
            nextOrderAt = now.plusSeconds(cycleDays * SECONDS_PER_DAY)
        }
    }

    /** 해지(AC4). 예정된 다음 회차부터 생성되지 않는다 — 이미 생성된 주문에는 영향 없음. */
    fun cancel(now: Instant) {
        status = DeliverySubscriptionStatus.CANCELED
        canceledAt = now
    }

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
    }
}
