package com.example.starter.domain.flashsale

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.flashsale.dto.FlashSaleResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 고객용 타임딜(한정특가) 조회 API. 인증 불필요(공개) — 게스트도 탐색 가능.
 * 진행 중인 딜만 노출한다(AC3). 실제 주문 시 적용 여부/가격은 [com.example.starter.domain.order.OrderService]
 * 가 주문 시점에 다시 서버에서 검증·계산한다(이 API 응답은 화면 표시용일 뿐 신뢰 가능한 가격이 아니다).
 */
@RestController
@RequestMapping("/api/flash-sales")
class FlashSaleController(
    private val flashSaleService: FlashSaleService,
) {

    @GetMapping
    fun listOngoing(): ApiResponse<List<FlashSaleResponse>> =
        ApiResponse.success(flashSaleService.listPublicOngoing())

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<FlashSaleResponse> =
        ApiResponse.success(flashSaleService.getPublicDetail(id))
}
