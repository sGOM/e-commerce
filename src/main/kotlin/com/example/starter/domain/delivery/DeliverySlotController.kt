package com.example.starter.domain.delivery

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.delivery.dto.DeliverySlotResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 고객용 배송 슬롯 조회 API. 인증 불필요(공개) — 게스트도 체크아웃 전에 조회할 수 있다.
 * 실제 예약은 주문 생성(`POST /api/orders`, `/api/orders/guest`)의 `deliverySlotSelections` 로 이뤄진다.
 */
@RestController
@RequestMapping("/api/delivery-slots")
class DeliverySlotController(
    private val deliverySlotService: DeliverySlotService,
) {

    @GetMapping
    fun list(
        @RequestParam(required = false) postalCode: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
    ): ApiResponse<List<DeliverySlotResponse>> =
        ApiResponse.success(deliverySlotService.listAvailable(postalCode, date))
}
