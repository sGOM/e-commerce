package com.example.starter.domain.subscription.entity

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
 * 정기배송 회차 처리 이력(append-only, AC10 마이페이지 타임라인 + 배치 감사로그 대용). 회차마다
 * 정확히 1건씩 쌓인다(성공/스킵/실패 무엇이든). [orderId] 는 실제 주문이 생성된 경우에만 채워진다.
 *
 * 배치가 만든 주문은 "행위자가 사람이 아니라 시스템"이라는 점에서 [AuditLogFilter]가 감사로그로 남기는
 * HTTP 요청 기반 로그와는 별개 트랙이다(§7 오픈이슈 #4) — 이 테이블이 곧 시스템 행위자의 감사로그
 * 역할을 겸한다(주문 자동생성/스킵/실패 사유를 모두 이 표에서 추적 가능).
 */
@Entity
@Table(name = "delivery_subscription_histories")
class DeliverySubscriptionHistory(
    @Column(name = "subscription_id", nullable = false)
    val subscriptionId: Long,

    @Column(name = "attempted_at", nullable = false)
    val attemptedAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val result: DeliverySubscriptionHistoryResult,

    @Column(name = "order_id")
    val orderId: Long? = null,

    @Column(length = 500)
    val detail: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
