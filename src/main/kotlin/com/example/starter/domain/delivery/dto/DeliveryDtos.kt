package com.example.starter.domain.delivery.dto

import com.example.starter.domain.delivery.entity.DeliveryRegion
import com.example.starter.domain.delivery.entity.DeliverySlot
import com.example.starter.domain.delivery.entity.DeliverySlotType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 배송 슬롯 개설 요청(관리자 전용, AC1). */
data class CreateDeliverySlotRequest(
    @field:NotNull val slotDate: LocalDate?,
    @field:NotNull val startTime: LocalTime?,
    @field:NotNull val endTime: LocalTime?,
    @field:NotNull val type: DeliverySlotType?,
    @field:NotNull val cutoffAt: Instant?,
    @field:NotNull val capacity: Int?,
    // 전국 공통 슬롯이면 비운다(AC2).
    val regionScope: String? = null,
    @field:PositiveOrZero val extraFee: Long = 0,
)

/** 관리자 슬롯 검색 조건(모두 선택적). */
data class AdminDeliverySlotSearchCondition(
    val date: LocalDate? = null,
    val type: DeliverySlotType? = null,
)

/** 배송 슬롯 응답 — [remaining]/[expired] 는 조회 시점 기준 파생 값이다. */
data class DeliverySlotResponse(
    val id: Long,
    val slotDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val type: DeliverySlotType,
    val cutoffAt: Instant,
    val capacity: Int,
    val reservedCount: Int,
    val remaining: Int,
    val regionScope: String?,
    val extraFee: Long,
    val expired: Boolean,
) {
    companion object {
        fun from(slot: DeliverySlot, now: Instant = Instant.now()) = DeliverySlotResponse(
            id = requireNotNull(slot.id),
            slotDate = slot.slotDate,
            startTime = slot.startTime,
            endTime = slot.endTime,
            type = slot.type,
            cutoffAt = slot.cutoffAt,
            capacity = slot.capacity,
            reservedCount = slot.reservedCount,
            remaining = slot.remaining,
            regionScope = slot.regionScope,
            extraFee = slot.extraFee,
            expired = slot.isExpired(now),
        )
    }
}

/** 새벽배송 가능 지역(우편번호 접두사 화이트리스트) 등록 요청(관리자 전용). */
data class CreateDeliveryRegionRequest(
    @field:NotBlank val postalCodePrefix: String?,
    val dawnDeliveryAvailable: Boolean = true,
)

/** 지역 새벽배송 가능 여부 변경 요청(관리자 전용). */
data class UpdateDeliveryRegionRequest(
    @field:NotNull val dawnDeliveryAvailable: Boolean?,
)

data class DeliveryRegionResponse(
    val id: Long,
    val postalCodePrefix: String,
    val dawnDeliveryAvailable: Boolean,
) {
    companion object {
        fun from(region: DeliveryRegion) = DeliveryRegionResponse(
            id = requireNotNull(region.id),
            postalCodePrefix = region.postalCodePrefix,
            dawnDeliveryAvailable = region.dawnDeliveryAvailable,
        )
    }
}
