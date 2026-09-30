package com.example.starter.domain.returns.entity

import com.example.starter.common.entity.BaseTimeEntity
import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
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
 * 반품 요청(ROADMAP 1.5) — 하위 주문(판매자) 단위. 반품 배송비·환불액은 요청 시점 정책으로 스냅샷해
 * 이후 정책이 바뀌어도 구매자에게 안내한 금액이 유지된다.
 */
@Entity
@Table(name = "order_returns")
class OrderReturn(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sub_order_id", nullable = false)
    val subOrder: SubOrder,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val reason: ReturnReason,

    @Column(length = 500)
    val detail: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_sub_order_status", nullable = false, length = 20)
    val previousSubOrderStatus: SubOrderStatus,

    @Column(name = "return_fee", nullable = false)
    val returnFee: Long,

    @Column(name = "refund_amount", nullable = false)
    val refundAmount: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReturnStatus = ReturnStatus.REQUESTED
        protected set

    /** 판매자가 요청을 받아들이고 회수를 시작한다. */
    fun approve() {
        transition(from = listOf(ReturnStatus.REQUESTED), to = ReturnStatus.COLLECTING)
    }

    /** 회수품 검수 통과. 환불은 호출자(SellerReturnService)가 이어서 처리한다. */
    fun complete() {
        transition(from = listOf(ReturnStatus.COLLECTING), to = ReturnStatus.COMPLETED)
    }

    /** 요청 단계 또는 검수 불합격으로 거절하고 하위 주문을 반품 전 상태로 돌린다. */
    fun reject() {
        transition(from = listOf(ReturnStatus.REQUESTED, ReturnStatus.COLLECTING), to = ReturnStatus.REJECTED)
        subOrder.status = previousSubOrderStatus
    }

    /** 주문 전체 환불로 반품이 필요 없어졌다 — 환불은 주문 취소 쪽이 이미 했다. */
    fun cancelByOrderRefund() {
        if (status.isOpen) status = ReturnStatus.CANCELED
    }

    private fun transition(from: List<ReturnStatus>, to: ReturnStatus) {
        // 관리자 전체 환불 등으로 하위 주문이 이미 반품 진행 상태를 벗어났다면 더 진행하지 않는다
        if (status !in from || subOrder.status != SubOrderStatus.RETURNING) {
            throw BusinessException(ErrorCode.RETURN_INVALID_TRANSITION)
        }
        status = to
    }
}
