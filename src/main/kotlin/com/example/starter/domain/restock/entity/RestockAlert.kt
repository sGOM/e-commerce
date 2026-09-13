package com.example.starter.domain.restock.entity

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
 * 재입고 알림 신청. 옵션(SKU) 단위 — 같은 상품이라도 다른 옵션은 재고가 있을 수 있어 상품 단위가 아닌
 * 옵션 단위로 신청받는다(`docs/planning/restock-alert.md` 4장).
 *
 * [userId]/[optionId] 는 다른 애그리거트 참조를 순수 id 로 두는 이 코드베이스 관례를 따른다
 * ([com.example.starter.domain.review.entity.Review] 참고).
 */
@Entity
@Table(name = "restock_alerts")
class RestockAlert(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "option_id", nullable = false)
    val optionId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RestockAlertStatus = RestockAlertStatus.PENDING
        protected set

    @Column(name = "notified_at")
    var notifiedAt: Instant? = null
        protected set

    /** 재입고 알림 발송 완료로 전이(AC5). 소멸성 상태 — 이후 재발송 대상에서 제외된다(AC6). */
    fun markNotified(at: Instant) {
        status = RestockAlertStatus.NOTIFIED
        notifiedAt = at
    }

    /** 사용자 취소(AC4). */
    fun cancel() {
        status = RestockAlertStatus.CANCELED
    }
}
