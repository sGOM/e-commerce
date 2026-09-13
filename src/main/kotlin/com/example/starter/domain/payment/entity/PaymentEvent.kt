package com.example.starter.domain.payment.entity

import com.example.starter.common.entity.BaseTimeEntity
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

/**
 * 결제 상태 전이 이력. 생성/승인/거절/취소 시점마다 한 행씩 쌓인다(감사·디버깅용).
 */
@Entity
@Table(name = "payment_events")
class PaymentEvent(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PaymentStatus,

    @Column(length = 500)
    val detail: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    lateinit var payment: Payment
}
