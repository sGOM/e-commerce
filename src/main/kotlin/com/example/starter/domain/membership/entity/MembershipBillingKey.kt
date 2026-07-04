package com.example.starter.domain.membership.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 회원의 정기결제용 빌링키. 회원당 1개만 유지하며 재등록 시 기존 값을 교체한다([replace]).
 * 원본 카드번호는 저장하지 않고([BaseTimeEntity.createdAt] 을 "등록 시각"으로 재사용),
 * 게이트웨이 발급 토큰([gatewayBillingKey])과 마지막 4자리만 보관한다.
 */
@Entity
@Table(name = "membership_billing_keys")
class MembershipBillingKey(
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
