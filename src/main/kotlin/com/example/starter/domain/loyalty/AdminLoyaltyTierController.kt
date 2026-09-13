package com.example.starter.domain.loyalty

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.loyalty.dto.AdminLoyaltyTierResponse
import com.example.starter.domain.loyalty.dto.LoyaltyTierBatchResult
import com.example.starter.domain.loyalty.entity.LoyaltyTier
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 로열티 등급 운영 API (`ROLE_ADMIN`). 등급별 회원 조회, 재계산 배치 수동 트리거
 * ([LoyaltyTierScheduler] 미가동 환경에서도 운영 가능).
 */
@RestController
@RequestMapping("/api/admin/loyalty-tiers")
class AdminLoyaltyTierController(
    private val loyaltyTierService: LoyaltyTierService,
    private val loyaltyTierBatchService: LoyaltyTierBatchService,
) {

    @GetMapping
    fun search(
        @RequestParam(defaultValue = "BRONZE") tier: LoyaltyTier,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<AdminLoyaltyTierResponse>> =
        ApiResponse.success(loyaltyTierService.searchForAdmin(tier, pageable))

    @GetMapping("/{userId}")
    fun getOne(@PathVariable userId: Long): ApiResponse<AdminLoyaltyTierResponse> =
        ApiResponse.success(loyaltyTierService.getForAdmin(userId))

    /** 등급 재계산 배치 수동 실행(스케줄러 비활성 환경 대비). */
    @PostMapping("/recalculate/run")
    fun runRecalculation(): ApiResponse<LoyaltyTierBatchResult> =
        ApiResponse.success(loyaltyTierBatchService.recalculateAll(), "로열티 등급 재계산 배치를 실행했습니다.")
}
