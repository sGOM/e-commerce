package com.example.starter.domain.settlement.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.domain.seller.entity.Seller
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
import jakarta.persistence.Table
import java.time.Instant

/**
 * 판매자 정산서. 미정산 SubOrder 들을 판매자 단위로 집계한 1건.
 * 지급액([payoutAmount]) = 판매액([salesAmount]) − 플랫폼 수수료([commissionAmount]).
 */
@Entity
@Table(name = "settlements")
class Settlement(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    val seller: Seller,

    @Column(name = "sales_amount", nullable = false)
    val salesAmount: Long,

    @Column(name = "commission_amount", nullable = false)
    val commissionAmount: Long,

    @Column(name = "payout_amount", nullable = false)
    val payoutAmount: Long,

    @Column(name = "settled_count", nullable = false)
    val settledCount: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: SettlementStatus = SettlementStatus.PENDING

    @Column(name = "paid_at")
    var paidAt: Instant? = null

    /** 판매대금 지급 완료 처리. */
    fun markPaid() {
        status = SettlementStatus.PAID
        paidAt = Instant.now()
    }
}
