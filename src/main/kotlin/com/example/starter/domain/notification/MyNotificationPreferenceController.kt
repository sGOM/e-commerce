package com.example.starter.domain.notification

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.notification.dto.NotificationPreferenceResponse
import com.example.starter.domain.notification.dto.UpdateNotificationPreferenceRequest
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 알림 수신 설정(메일·푸시, 종류별). 인앱 알림함은 항상 받는다. */
@RestController
@RequestMapping("/api/me/notification-preferences")
class MyNotificationPreferenceController(
    private val notificationPreferenceService: NotificationPreferenceService,
) {

    @GetMapping
    fun get(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<NotificationPreferenceResponse>> =
        ApiResponse.success(notificationPreferenceService.get(principal.userId))

    @PutMapping
    fun update(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody request: List<UpdateNotificationPreferenceRequest>,
    ): ApiResponse<List<NotificationPreferenceResponse>> =
        ApiResponse.success(notificationPreferenceService.update(principal.userId, request), "알림 설정을 저장했습니다.")
}
