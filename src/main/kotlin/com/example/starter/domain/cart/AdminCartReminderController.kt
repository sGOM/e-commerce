package com.example.starter.domain.cart

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.cart.dto.CartReminderBatchResult
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 관리자 장바구니 이탈 리마인드 배치 수동 트리거 API (`ROLE_ADMIN`). */
@RestController
@RequestMapping("/api/admin/cart-reminders")
class AdminCartReminderController(
    private val cartReminderBatchService: CartReminderBatchService,
) {

    @PostMapping("/run")
    fun run(): ApiResponse<CartReminderBatchResult> =
        ApiResponse.success(cartReminderBatchService.sendReminders(), "장바구니 이탈 리마인드 배치를 실행했습니다.")
}
