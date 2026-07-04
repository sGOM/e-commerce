package com.example.starter.domain.delivery

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.delivery.dto.CreateDeliveryRegionRequest
import com.example.starter.domain.delivery.dto.DeliveryRegionResponse
import com.example.starter.domain.delivery.dto.UpdateDeliveryRegionRequest
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 새벽배송 가능 지역(우편번호 접두사 화이트리스트) 관리 API (`ROLE_ADMIN`).
 * 기획서(§5)는 `PATCH /api/admin/delivery-regions` 단일 엔드포인트를 제안했으나, 등록/조회/삭제가
 * 분리된 리소스 CRUD 가 이 코드베이스의 관리자 API 관례(예: `/api/admin/flash-sales`)와 더 일관되어
 * 리소스 단위 REST 로 확정했다(id 단위 PATCH 로 새벽배송 가능 여부만 토글).
 */
@RestController
@RequestMapping("/api/admin/delivery-regions")
@PreAuthorize("hasRole('ADMIN')")
class AdminDeliveryRegionController(
    private val deliverySlotService: DeliverySlotService,
) {

    @GetMapping
    fun list(): ApiResponse<List<DeliveryRegionResponse>> =
        ApiResponse.success(deliverySlotService.listRegions())

    @PostMapping
    fun create(@RequestBody @Valid request: CreateDeliveryRegionRequest): ApiResponse<DeliveryRegionResponse> =
        ApiResponse.success(deliverySlotService.createRegion(request), "배송 가능 지역을 등록했습니다.")

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateDeliveryRegionRequest,
    ): ApiResponse<DeliveryRegionResponse> =
        ApiResponse.success(
            deliverySlotService.updateRegion(id, requireNotNull(request.dawnDeliveryAvailable)),
            "지역 설정을 변경했습니다.",
        )

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ApiResponse<Unit> {
        deliverySlotService.deleteRegion(id)
        return ApiResponse.success("지역을 삭제했습니다.")
    }
}
