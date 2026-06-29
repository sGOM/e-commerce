package com.example.starter.domain.payment

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.payment.dto.PaymentResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결제 API (인증 필요). 본인 주문에 대해서만 결제할 수 있다. 게스트 결제는 추후 단계.
 */
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
) {

    @PostMapping("/{orderId}")
    fun pay(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable orderId: Long,
    ): ApiResponse<PaymentResponse> =
        ApiResponse.success(paymentService.pay(principal.userId, orderId), "결제가 완료되었습니다.")
}
