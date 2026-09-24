package com.example.starter.domain.subscription

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.subscription.dto.CreateDeliverySubscriptionRequest
import com.example.starter.domain.subscription.dto.DeliverySubscriptionBillingKeyResponse
import com.example.starter.domain.subscription.dto.DeliverySubscriptionHistoryResponse
import com.example.starter.domain.subscription.dto.DeliverySubscriptionResponse
import com.example.starter.domain.subscription.dto.RegisterDeliverySubscriptionBillingKeyRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 정기배송 API (인증 필요, 게스트 미지원). 카드 등록 → 등록/조회/일시정지/재개/스킵/해지.
 */
@RestController
@RequestMapping("/api/me/delivery-subscriptions")
class DeliverySubscriptionController(
    private val subscriptionService: DeliverySubscriptionService,
    private val billingService: DeliverySubscriptionBillingService,
) {

    @PostMapping("/billing-key")
    fun registerBillingKey(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: RegisterDeliverySubscriptionBillingKeyRequest,
    ): ApiResponse<DeliverySubscriptionBillingKeyResponse> =
        ApiResponse.success(subscriptionService.registerBillingKey(principal.userId, request), "카드가 등록되었습니다.")

    @PostMapping
    fun register(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateDeliverySubscriptionRequest,
    ): ApiResponse<DeliverySubscriptionResponse> =
        ApiResponse.success(subscriptionService.register(principal.userId, request), "정기배송이 등록되었습니다.")

    @GetMapping
    fun getMy(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<DeliverySubscriptionResponse>> =
        ApiResponse.success(subscriptionService.getMy(principal.userId))

    @GetMapping("/{id}/histories")
    fun getHistories(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<List<DeliverySubscriptionHistoryResponse>> =
        ApiResponse.success(billingService.getHistories(principal.userId, id))

    @PatchMapping("/{id}/pause")
    fun pause(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<DeliverySubscriptionResponse> =
        ApiResponse.success(subscriptionService.pause(principal.userId, id), "정기배송이 일시정지되었습니다.")

    @PatchMapping("/{id}/resume")
    fun resume(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<DeliverySubscriptionResponse> =
        ApiResponse.success(subscriptionService.resume(principal.userId, id), "정기배송이 재개되었습니다.")

    @PostMapping("/{id}/skip-next")
    fun skipNext(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<DeliverySubscriptionResponse> =
        ApiResponse.success(subscriptionService.skipNext(principal.userId, id), "다음 회차를 건너뜁니다.")

    @DeleteMapping("/{id}")
    fun cancel(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<DeliverySubscriptionResponse> =
        ApiResponse.success(subscriptionService.cancel(principal.userId, id), "정기배송이 해지되었습니다.")
}
