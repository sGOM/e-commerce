package com.example.starter.domain.flashsale

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.flashsale.dto.AdminFlashSaleSearchCondition
import com.example.starter.domain.flashsale.dto.CreateFlashSaleRequest
import com.example.starter.domain.flashsale.dto.FlashSaleResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 타임딜 편성 API (`ROLE_ADMIN`). 전체 옵션에 직접 등록할 수 있고, 셀러가 신청한 타임딜을
 * 포함해 언제든 강제 종료할 수 있다(오픈 이슈 #1 결정, [FlashSaleService] 문서 참고).
 */
@RestController
@RequestMapping("/api/admin/flash-sales")
@PreAuthorize("hasRole('ADMIN')")
class AdminFlashSaleController(
    private val flashSaleService: FlashSaleService,
) {

    @GetMapping
    fun search(
        condition: AdminFlashSaleSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<FlashSaleResponse>> =
        ApiResponse.success(flashSaleService.searchForAdmin(condition, pageable))

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<FlashSaleResponse> =
        ApiResponse.success(flashSaleService.getDetailForAdmin(id))

    @PostMapping
    fun create(@RequestBody @Valid request: CreateFlashSaleRequest): ApiResponse<FlashSaleResponse> =
        ApiResponse.success(flashSaleService.createByAdmin(request), "타임딜을 등록했습니다.")

    @PatchMapping("/{id}/cancel")
    fun cancel(@PathVariable id: Long): ApiResponse<FlashSaleResponse> =
        ApiResponse.success(flashSaleService.cancelByAdmin(id), "타임딜을 강제 종료했습니다.")
}
