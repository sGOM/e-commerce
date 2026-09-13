package com.example.starter.domain.subscription.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 정기배송 정기결제용 빌링키. [com.example.starter.domain.membership.entity.MembershipBillingKey] 와
 * 같은 관례(회원당 1개, 재등록 시 교체, 원본 카드번호 미저장)를 따르되 별도 테이블로 둔다 — 두 기능이
 * 같은 [com.example.starter.domain.billing.gateway.BillingKeyGateway] 포트/어댑터는 공유하지만
 * (중복 PG 연동 방지가 목적), "카드 등록" 자체를 공용 테이블로 통합할지는 기획서 §9 오픈이슈 #1로
 * 남겨둔다 — 지금은 도메인 경계를 우선해 분리했다(회원이 멤버십/정기배송에 다른 카드를 쓰고 싶을 수도
 * 있고, 한 도메인의 스키마 변경이 다른 도메인에 영향을 주지 않게 하려는 목적). 후속 과제로 "결제수단"
 * 공용 도메인 통합을 검토한다.
 */
@Entity
@Table(name = "delivery_subscription_billing_keys")
class DeliverySubscriptionBillingKey(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(name = "gateway_billing_key", nullable = false, length = 200)
    var gatewayBillingKey: String,

    @Column(name = "card_last4", nullable = false, length = 4)
    var cardLast4: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    /** 카드 재등록(재발급 등) — 기존 행을 그대로 갱신한다. */
    fun replace(gatewayBillingKey: String, cardLast4: String) {
        this.gatewayBillingKey = gatewayBillingKey
        this.cardLast4 = cardLast4
    }
}
