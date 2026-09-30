package com.example.starter.domain.notification.push

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.notification.dto.PushPublicKeyResponse
import com.example.starter.domain.notification.dto.SubscribePushRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 회원 웹 푸시 구독. 해지는 브라우저에서 구독을 끄면 다음 발송 때 푸시 서비스의 410 으로 정리된다. */
@RestController
@RequestMapping("/api/me/push-subscriptions")
class MyPushSubscriptionController(
    private val pushSubscriptionService: PushSubscriptionService,
) {

    @GetMapping("/public-key")
    fun publicKey(): ApiResponse<PushPublicKeyResponse> = ApiResponse.success(pushSubscriptionService.publicKey())

    @PostMapping
    fun subscribe(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: SubscribePushRequest,
    ): ApiResponse<Unit> {
        pushSubscriptionService.subscribe(principal.userId, request)
        return ApiResponse.success("푸시 알림을 켰습니다.")
    }
}
