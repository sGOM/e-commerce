package com.example.starter.domain.point.entity

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
import java.time.Instant

/**
 * 적립 lot — 한 번의 적립분. 만료일([expiresAt])과 잔여([remaining])를 가지며,
 * 사용은 만료 임박 순으로 [remaining] 을 깎고, 만료/회수는 잔여를 소멸시킨다.
 */
@Entity
@Table(name = "point_lots")
class PointLot(
    @Column(nullable = false)
    val amount: Long,

    @Column(nullable = false)
    var remaining: Long,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "source_order_id")
    val sourceOrderId: Long? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    lateinit var account: PointAccount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PointLotStatus = PointLotStatus.ACTIVE

    val isUsable: Boolean
        get() = status == PointLotStatus.ACTIVE && remaining > 0

    /** 잔여에서 [take] 만큼 사용 차감. 모두 소진되면 EXHAUSTED. */
    fun consume(take: Long) {
        remaining -= take
        if (remaining <= 0L) {
            status = PointLotStatus.EXHAUSTED
        }
    }

    /** 남은 잔여를 만료/회수로 소멸시키고 그 양을 반환. */
    fun expireRemaining(): Long {
        val left = remaining
        remaining = 0
        status = PointLotStatus.EXPIRED
        return left
    }
}
