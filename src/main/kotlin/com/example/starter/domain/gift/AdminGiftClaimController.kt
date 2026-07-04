package com.example.starter.domain.gift

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.gift.dto.GiftClaimResponse
import com.example.starter.domain.gift.dto.GiftExpiryBatchResult
import com.example.starter.domain.gift.dto.GiftPolicyResponse
import com.example.starter.domain.gift.dto.UpdateGiftPolicyRequest
import com.example.starter.domain.gift.entity.GiftClaimStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 선물하기 운영 API (`ROLE_ADMIN`). 링크 상태 모니터링, 만료기한 정책 관리,
 * 미수락 만료 배치 수동 트리거([GiftExpiryScheduler] 미가동 환경에서도 운영 가능).
 */
@RestController
@RequestMapping("/api/admin/gift-claims")
class AdminGiftClaimController(
    private val adminGiftClaimService: AdminGiftClaimService,
    private val giftPolicyService: GiftPolicyService,
    private val giftExpiryBatchService: GiftExpiryBatchService,
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) status: GiftClaimStatus?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<GiftClaimResponse>> =
        ApiResponse.success(adminGiftClaimService.search(status, pageable))

    @GetMapping("/policy")
    fun getPolicy(): ApiResponse<GiftPolicyResponse> =
        ApiResponse.success(giftPolicyService.getPolicy())

    @PatchMapping("/policy")
    fun updatePolicy(@RequestBody request: UpdateGiftPolicyRequest): ApiResponse<GiftPolicyResponse> =
        ApiResponse.success(giftPolicyService.update(request.expiryDays), "선물 링크 정책을 변경했습니다.")

    /** 선물 미수락 만료 배치 수동 실행(스케줄러 비활성 환경 대비). */
    @PostMapping("/expire/run")
    fun runExpiry(): ApiResponse<GiftExpiryBatchResult> =
        ApiResponse.success(giftExpiryBatchService.expireDueClaims(), "선물 만료 배치를 실행했습니다.")
}
