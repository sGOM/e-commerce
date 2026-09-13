package com.example.starter.domain.delivery

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.delivery.dto.AdminDeliverySlotSearchCondition
import com.example.starter.domain.delivery.dto.CreateDeliveryRegionRequest
import com.example.starter.domain.delivery.dto.CreateDeliverySlotRequest
import com.example.starter.domain.delivery.dto.DeliveryRegionResponse
import com.example.starter.domain.delivery.dto.DeliverySlotResponse
import com.example.starter.domain.delivery.entity.DeliveryRegion
import com.example.starter.domain.delivery.entity.DeliverySlot
import com.example.starter.domain.delivery.entity.DeliverySlotType
import com.example.starter.domain.delivery.repository.DeliveryRegionRepository
import com.example.starter.domain.delivery.repository.DeliverySlotRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 배송 슬롯 마스터 관리(관리자) / 공개 조회. 실제 예약·정원 증감은 주문 흐름
 * ([com.example.starter.domain.order.OrderService])이 [DeliverySlotRepository] 를 직접 사용해
 * SubOrder 조립과 같은 트랜잭션에서 처리한다(`docs/planning/delivery-slot.md`).
 */
@Service
@Transactional(readOnly = true)
class DeliverySlotService(
    private val deliverySlotRepository: DeliverySlotRepository,
    private val deliveryRegionRepository: DeliveryRegionRepository,
) {

    /**
     * 공개 목록(AC3/AC4/AC5) — [date] 없으면 오늘(KST). [postalCode] 가 새벽배송 화이트리스트에
     * 없으면 DAWN 슬롯은 목록에서 제외한다(DAYTIME 은 지역 제한 없이 노출).
     */
    fun listAvailable(postalCode: String?, date: LocalDate?): List<DeliverySlotResponse> {
        val targetDate = date ?: LocalDate.now(KST)
        val now = Instant.now()
        val dawnEligible = postalCode != null && deliveryRegionRepository.existsDawnAvailable(postalCode)
        return deliverySlotRepository.findAvailable(targetDate, postalCode, now)
            .filter { it.type != DeliverySlotType.DAWN || dawnEligible }
            .map { DeliverySlotResponse.from(it, now) }
    }

    @Transactional
    fun createByAdmin(request: CreateDeliverySlotRequest): DeliverySlotResponse {
        val slotDate = requireNotNull(request.slotDate)
        val startTime = requireNotNull(request.startTime)
        val endTime = requireNotNull(request.endTime)
        if (!endTime.isAfter(startTime)) {
            throw BusinessException(ErrorCode.DELIVERY_SLOT_INVALID_PERIOD)
        }
        val cutoffAt = requireNotNull(request.cutoffAt)
        val slotStartInstant = slotDate.atTime(startTime).atZone(KST).toInstant()
        if (!cutoffAt.isBefore(slotStartInstant)) {
            throw BusinessException(ErrorCode.DELIVERY_SLOT_INVALID_CUTOFF)
        }
        val slot = deliverySlotRepository.save(
            DeliverySlot(
                slotDate = slotDate,
                startTime = startTime,
                endTime = endTime,
                type = requireNotNull(request.type),
                cutoffAt = cutoffAt,
                capacity = requireNotNull(request.capacity),
                regionScope = request.regionScope,
                extraFee = request.extraFee,
            ),
        )
        return DeliverySlotResponse.from(slot)
    }

    fun searchForAdmin(condition: AdminDeliverySlotSearchCondition, pageable: Pageable): PageResponse<DeliverySlotResponse> {
        val now = Instant.now()
        val page = deliverySlotRepository.findPage(pageable) {
            select(entity(DeliverySlot::class))
                .from(entity(DeliverySlot::class))
                .whereAnd(
                    condition.date?.let { path(DeliverySlot::slotDate).eq(it) },
                    condition.type?.let { path(DeliverySlot::type).eq(it) },
                )
                .orderBy(path(DeliverySlot::slotDate).desc(), path(DeliverySlot::startTime).asc())
        }
        return PageResponse.of(page) { DeliverySlotResponse.from(requireNotNull(it), now) }
    }

    fun getDetailForAdmin(id: Long): DeliverySlotResponse = DeliverySlotResponse.from(findById(id))

    private fun findById(id: Long): DeliverySlot =
        deliverySlotRepository.findById(id).orElseThrow { BusinessException(ErrorCode.DELIVERY_SLOT_NOT_FOUND) }

    // ── 새벽배송 가능 지역 화이트리스트(관리자) ──────────────────────────────────
    fun listRegions(): List<DeliveryRegionResponse> =
        deliveryRegionRepository.findAllByOrderByPostalCodePrefixAsc().map { DeliveryRegionResponse.from(it) }

    @Transactional
    fun createRegion(request: CreateDeliveryRegionRequest): DeliveryRegionResponse {
        val prefix = requireNotNull(request.postalCodePrefix)
        if (deliveryRegionRepository.existsByPostalCodePrefix(prefix)) {
            throw BusinessException(ErrorCode.DELIVERY_REGION_ALREADY_EXISTS)
        }
        val region = deliveryRegionRepository.save(
            DeliveryRegion(postalCodePrefix = prefix, dawnDeliveryAvailable = request.dawnDeliveryAvailable),
        )
        return DeliveryRegionResponse.from(region)
    }

    @Transactional
    fun updateRegion(id: Long, dawnDeliveryAvailable: Boolean): DeliveryRegionResponse {
        val region = deliveryRegionRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.DELIVERY_REGION_NOT_FOUND) }
        region.dawnDeliveryAvailable = dawnDeliveryAvailable
        return DeliveryRegionResponse.from(region)
    }

    @Transactional
    fun deleteRegion(id: Long) {
        if (!deliveryRegionRepository.existsById(id)) {
            throw BusinessException(ErrorCode.DELIVERY_REGION_NOT_FOUND)
        }
        deliveryRegionRepository.deleteById(id)
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
    }
}
