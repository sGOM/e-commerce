package com.example.starter.domain.notification

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.notification.dto.MyNotificationsResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 마이페이지 인앱 알림함 API (인증 필요).
 */
@RestController
@RequestMapping("/api/me/notifications")
class MyNotificationController(
    private val notificationService: NotificationService,
) {

    @GetMapping
    fun myNotifications(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<MyNotificationsResponse> =
        ApiResponse.success(notificationService.getMyNotifications(principal.userId, unreadOnly, pageable))

    @PatchMapping("/{id}/read")
    fun markRead(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        notificationService.markRead(principal.userId, id)
        return ApiResponse.success("알림을 읽음 처리했습니다.")
    }
}
