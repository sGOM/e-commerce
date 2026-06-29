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

/**
 * 포인트 원장 항목. 적립/사용/환원 시점마다 한 행씩 쌓이며, 잔액의 근거가 된다.
 */
@Entity
@Table(name = "point_transactions")
class PointTransaction(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: PointTransactionType,

    @Column(nullable = false)
    val amount: Long, // 항상 양수

    @Column(name = "order_id")
    val orderId: Long? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    lateinit var account: PointAccount
}
