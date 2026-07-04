package com.example.starter.domain.delivery

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.delivery.dto.AdminDeliverySlotSearchCondition
import com.example.starter.domain.delivery.dto.CreateDeliverySlotRequest
import com.example.starter.domain.delivery.dto.DeliverySlotResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 관리자 배송 슬롯 개설/조회 API (`ROLE_ADMIN`, AC1). */
@RestController
@RequestMapping("/api/admin/delivery-slots")
@PreAuthorize("hasRole('ADMIN')")
class AdminDeliverySlotController(
    private val deliverySlotService: DeliverySlotService,
) {

    @GetMapping
    fun search(
        condition: AdminDeliverySlotSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<DeliverySlotResponse>> =
        ApiResponse.success(deliverySlotService.searchForAdmin(condition, pageable))

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<DeliverySlotResponse> =
        ApiResponse.success(deliverySlotService.getDetailForAdmin(id))

    @PostMapping
    fun create(@RequestBody @Valid request: CreateDeliverySlotRequest): ApiResponse<DeliverySlotResponse> =
        ApiResponse.success(deliverySlotService.createByAdmin(request), "배송 슬롯을 등록했습니다.")
}
