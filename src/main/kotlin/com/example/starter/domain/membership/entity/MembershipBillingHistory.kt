package com.example.starter.domain.membership.entity

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
 * 정기결제 시도 이력(성공/실패 모두 append). [cycleAt] 은 이 시도가 속한 청구 주기의 식별자로,
 * 시도 시점의 [Membership.nextBillingAt] 스냅샷이다. 같은 주기에 재시도로 여러 FAILED 행이 쌓일 수
 * 있으나, SUCCESS 는 DB 부분 유니크 인덱스(`uk_membership_billing_histories_success_cycle`)로 주기당
 * 1건만 허용해 중복 청구를 막는다(멱등 처리, 기획서 §4).
 */
@Entity
@Table(name = "membership_billing_histories")
class MembershipBillingHistory(
    @Column(name = "membership_id", nullable = false)
    val membershipId: Long,

    @Column(name = "cycle_at", nullable = false)
    val cycleAt: Instant,

    @Column(name = "attempted_at", nullable = false)
    val attemptedAt: Instant,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: BillingHistoryStatus,

    @Column(name = "failure_reason", length = 500)
    val failureReason: String? = null,

    @Column(name = "gateway_transaction_id", length = 100)
    val gatewayTransactionId: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
