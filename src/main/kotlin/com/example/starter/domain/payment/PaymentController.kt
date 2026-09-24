package com.example.starter.domain.payment

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.payment.dto.GuestPayRequest
import com.example.starter.domain.payment.dto.PayRequest
import com.example.starter.domain.payment.dto.PaymentResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 결제 API. 회원은 본인 주문(`/{orderId}`), 비회원은 주문번호 + 연락처 확인(`/guest`, 공개 경로)으로 결제한다.
 */
@RestController
@RequestMapping("/api/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val paymentWebhookService: PaymentWebhookService,
) {

    /** 토스 결제 웹훅(공개·CSRF 제외). 10초 안에 200 을 받지 못하면 토스가 재전송하므로 처리 오류는 그대로 5xx 로 둔다. */
    @PostMapping("/webhook/toss")
    fun tossWebhook(@RequestBody request: TossWebhookRequest) = paymentWebhookService.handleToss(request)

    @PostMapping("/guest")
    fun payGuest(@RequestBody @Valid request: GuestPayRequest): ApiResponse<PaymentResponse> =
        ApiResponse.success(
            paymentService.payGuest(request.orderNumber!!, request.ordererPhone!!, request.paymentKey),
            "결제가 완료되었습니다.",
        )

    @PostMapping("/{orderId}")
    fun pay(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable orderId: Long,
        @RequestBody(required = false) request: PayRequest?,
    ): ApiResponse<PaymentResponse> =
        ApiResponse.success(
            paymentService.pay(principal.userId, orderId, request?.paymentKey),
            "결제가 완료되었습니다.",
        )
}
