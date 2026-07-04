package com.example.starter.domain.subscription

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.subscription.dto.AdminDeliverySubscriptionResponse
import com.example.starter.domain.subscription.dto.AdminDeliverySubscriptionSearchCondition
import com.example.starter.domain.subscription.dto.DeliverySubscriptionHistoryResponse
import com.example.starter.domain.subscription.dto.DeliverySubscriptionPolicyResponse
import com.example.starter.domain.subscription.dto.UpdateDeliverySubscriptionPolicyRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 정기배송 운영 API (`ROLE_ADMIN`). 구독 현황 검색, 회차 이력 조회, 정책 관리,
 * 배치 수동 트리거([DeliverySubscriptionBillingScheduler] 미가동 환경에서도 운영 가능).
 */
@RestController
@RequestMapping("/api/admin/delivery-subscriptions")
class AdminDeliverySubscriptionController(
    private val billingService: DeliverySubscriptionBillingService,
    private val policyService: DeliverySubscriptionPolicyService,
) {

    @GetMapping
    fun search(
        condition: AdminDeliverySubscriptionSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<AdminDeliverySubscriptionResponse>> =
        ApiResponse.success(billingService.search(condition, pageable))

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<AdminDeliverySubscriptionResponse> =
        ApiResponse.success(billingService.getDetailForAdmin(id))

    @GetMapping("/{id}/histories")
    fun getHistories(@PathVariable id: Long): ApiResponse<List<DeliverySubscriptionHistoryResponse>> =
        ApiResponse.success(billingService.getHistoriesForAdmin(id))

    /** 정기배송 배치 수동 실행(스케줄러 비활성 환경 대비). */
    @PostMapping("/billing/run")
    fun runBilling(): ApiResponse<DeliverySubscriptionBillingRunResult> =
        ApiResponse.success(billingService.runDueCycles(), "정기배송 배치를 실행했습니다.")

    @GetMapping("/policy")
    fun getPolicy(): ApiResponse<DeliverySubscriptionPolicyResponse> =
        ApiResponse.success(policyService.getPolicy())

    @PatchMapping("/policy")
    fun updatePolicy(@RequestBody @Valid request: UpdateDeliverySubscriptionPolicyRequest): ApiResponse<DeliverySubscriptionPolicyResponse> =
        ApiResponse.success(policyService.update(request), "정기배송 정책을 변경했습니다.")
}
