package com.example.starter.domain.delivery.entity

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
import java.time.LocalDate
import java.time.LocalTime

/**
 * 배송 슬롯(새벽배송/시간대 지정, `docs/planning/delivery-slot.md`). 관리자가 개설하며,
 * 주문(체크아웃) 시점에 [com.example.starter.domain.order.entity.SubOrder] 단위로 선택·예약된다(AC6).
 *
 * [regionScope] 는 이 슬롯이 서비스되는 권역(우편번호 접두사, [DeliveryRegion.postalCodePrefix] 와 동일
 * 형식)이며 NULL 이면 전국 공통이다. 새벽배송 가능 여부 자체([DeliveryRegion.dawnDeliveryAvailable])와는
 * 별개 개념 — 전자는 "이 배송지가 새벽배송을 받을 수 있는가", 후자는 "이 특정 슬롯이 어느 권역을 서비스
 * 하는가"이다.
 */
@Entity
@Table(name = "delivery_slots")
class DeliverySlot(
    @Column(name = "slot_date", nullable = false)
    val slotDate: LocalDate,

    @Column(name = "start_time", nullable = false)
    val startTime: LocalTime,

    @Column(name = "end_time", nullable = false)
    val endTime: LocalTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: DeliverySlotType,

    // 주문 마감 시각. 이 시각이 지나면 선택 목록에서 제외되고 예약도 거부된다(AC4/AC7).
    @Column(name = "cutoff_at", nullable = false)
    val cutoffAt: Instant,

    @Column(nullable = false)
    val capacity: Int,

    @Column(name = "region_scope", length = 10)
    val regionScope: String? = null,

    // 슬롯 이용 추가 배송비(원). 공통 배송비 모델 부재(오픈 이슈)와 별개로, 슬롯 단위에 한해 우선
    // 도입한다 — 실제 결제 반영은 SubOrder.deliveryFee 스냅샷을 통해 이뤄진다(OrderService 참고).
    @Column(name = "extra_fee", nullable = false)
    val extraFee: Long = 0,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // 재고 reserved/타임딜 soldQuantity 와 동일한 이유로, 원자적 UPDATE(DeliverySlotRepository.reserve/
    // release)로만 변경한다. JPA 변경감지로 직접 갱신하지 않는다.
    @Column(name = "reserved_count", nullable = false)
    var reservedCount: Int = 0

    val remaining: Int
        get() = (capacity - reservedCount).coerceAtLeast(0)

    fun isExpired(at: Instant): Boolean = !cutoffAt.isAfter(at)

    fun isFull(): Boolean = reservedCount >= capacity
}
