package com.example.starter.domain.payment.entity

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
import jakarta.persistence.Table

/**
 * 결제 본체 — 주문([orderId]) 단위 1건. order_id UNIQUE 로 중복 결제를 막는다(멱등성의 토대).
 * 상태 전이는 [PaymentEvent] 로 이력을 남긴다.
 */
@Entity
@Table(name = "payments")
class Payment(
    @Column(name = "order_id", nullable = false, unique = true)
    val orderId: Long,

    @Column(nullable = false)
    val amount: Long,

    @Column(nullable = false, length = 20)
    val method: String = "MOCK",
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PaymentStatus = PaymentStatus.READY

    @Column(name = "pg_transaction_id", length = 100)
    var pgTransactionId: String? = null

    @OneToMany(mappedBy = "payment", cascade = [CascadeType.ALL], orphanRemoval = true)
    val events: MutableList<PaymentEvent> = mutableListOf()

    init {
        addEvent(PaymentStatus.READY, "결제 생성")
    }

    /** 승인 성공 처리 + 이력 기록 */
    fun markPaid(transactionId: String?) {
        status = PaymentStatus.PAID
        pgTransactionId = transactionId
        addEvent(PaymentStatus.PAID, "승인 완료 (tx=$transactionId)")
    }

    /** 승인 거절 처리 + 이력 기록 */
    fun markFailed(reason: String) {
        status = PaymentStatus.FAILED
        addEvent(PaymentStatus.FAILED, reason)
    }

    /** 결제 취소/환불 처리 + 이력 기록 */
    fun markCanceled(reason: String) {
        status = PaymentStatus.CANCELED
        addEvent(PaymentStatus.CANCELED, reason)
    }

    /** 부분 환불 기록(일부 하위 주문 취소). 결제 상태는 유지하고 이력만 남긴다. */
    fun recordPartialRefund(amount: Long) {
        addEvent(status, "부분 환불 ${amount}원")
    }

    private fun addEvent(status: PaymentStatus, detail: String) {
        val event = PaymentEvent(status = status, detail = detail)
        event.payment = this
        events.add(event)
    }
}
