package com.example.starter.domain.point.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

/**
 * 회원 포인트 계좌. [balance] 는 활성 적립 lot([lots]) 잔여 합의 캐시이며, 모든 변동은 원장([transactions])을 동반한다.
 *
 * 적립은 만료일을 가진 lot 을 만들고, 사용은 **만료 임박 순(FIFO)** 으로 잔여를 깎는다.
 * 만료/회수는 lot 의 남은 잔여만 소멸시켜 사용분과 이중 차감되지 않는다.
 */
@Entity
@Table(name = "point_accounts")
class PointAccount(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false)
    var balance: Long = 0

    @OneToMany(mappedBy = "account", cascade = [CascadeType.ALL], orphanRemoval = true)
    val lots: MutableList<PointLot> = mutableListOf()

    @OneToMany(mappedBy = "account", cascade = [CascadeType.ALL], orphanRemoval = true)
    val transactions: MutableList<PointTransaction> = mutableListOf()

    /** 적립 — 만료일을 가진 lot 을 새로 만든다. */
    fun earn(amount: Long, orderId: Long?, expiresAt: Instant) {
        if (amount <= 0) return
        addLot(amount, expiresAt, orderId)
        balance += amount
        record(PointTransactionType.EARN, amount, orderId)
    }

    /** 사용 — 만료 임박 순으로 lot 잔여를 깎는다. 잔액 부족이면 false 반환(변동 없음). */
    fun use(amount: Long, orderId: Long?): Boolean {
        if (amount <= 0) return true
        if (balance < amount) return false
        var remaining = amount
        usableLotsByExpiry().forEach { lot ->
            if (remaining <= 0) return@forEach
            val take = minOf(lot.remaining, remaining)
            lot.consume(take)
            remaining -= take
        }
        balance -= amount
        record(PointTransactionType.USE, amount, orderId)
        return true
    }

    /** 사용 취소 환원 — 새 lot 으로 적립한다(만료일 재부여). */
    fun cancelUse(amount: Long, orderId: Long?, expiresAt: Instant) {
        if (amount <= 0) return
        addLot(amount, expiresAt, orderId)
        balance += amount
        record(PointTransactionType.CANCEL_USE, amount, orderId)
    }

    /** 특정 주문으로 적립된 lot 의 잔여를 회수(결제 취소/환불). 이미 쓴 분은 회수하지 않는다. */
    fun revokeEarnByOrder(orderId: Long): Long {
        var revoked = 0L
        lots.filter { it.isUsable && it.sourceOrderId == orderId }.forEach { revoked += it.expireRemaining() }
        if (revoked > 0) {
            balance -= revoked
            record(PointTransactionType.CANCEL_EARN, revoked, orderId)
        }
        return revoked
    }

    /** 만료일이 지난 lot 의 잔여를 소멸시킨다. 만료된 총량을 반환. */
    fun expireDue(now: Instant): Long {
        var expired = 0L
        lots.filter { it.isUsable && !it.expiresAt.isAfter(now) }.forEach { lot ->
            val amount = lot.expireRemaining()
            expired += amount
            record(PointTransactionType.EXPIRE, amount, lot.sourceOrderId)
        }
        if (expired > 0) {
            balance -= expired
        }
        return expired
    }

    private fun usableLotsByExpiry(): List<PointLot> =
        lots.filter { it.isUsable }.sortedBy { it.expiresAt }

    private fun addLot(amount: Long, expiresAt: Instant, orderId: Long?) {
        val lot = PointLot(amount = amount, remaining = amount, expiresAt = expiresAt, sourceOrderId = orderId)
        lot.account = this
        lots.add(lot)
    }

    private fun record(type: PointTransactionType, amount: Long, orderId: Long?) {
        val txn = PointTransaction(type = type, amount = amount, orderId = orderId)
        txn.account = this
        transactions.add(txn)
    }
}
