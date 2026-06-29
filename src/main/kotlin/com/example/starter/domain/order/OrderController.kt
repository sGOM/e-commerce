package com.example.starter.domain.order

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.order.dto.CreateOrderRequest
import com.example.starter.domain.order.dto.GuestOrderLookupRequest
import com.example.starter.domain.order.dto.GuestOrderRequest
import com.example.starter.domain.order.dto.OrderResponse
import com.example.starter.domain.order.dto.OrderSummaryResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 주문 API (인증 필요). 주문 생성·조회·취소는 모두 인증된 본인 주문에만 적용된다.
 */
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
) {

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateOrderRequest,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.createFromCart(principal.userId, request), "주문이 생성되었습니다.")

    /** 게스트(비회원) 주문 생성. 항목을 직접 전달하며 쿠폰/포인트는 미지원. 인증 불필요. */
    @PostMapping("/guest")
    fun createGuest(@RequestBody @Valid request: GuestOrderRequest): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.createGuestOrder(request), "주문이 생성되었습니다.")

    /** 게스트 주문 조회 — 주문번호 + 연락처. 인증 불필요. */
    @PostMapping("/guest/lookup")
    fun lookupGuest(@RequestBody @Valid request: GuestOrderLookupRequest): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.lookupGuestOrder(request))

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<OrderSummaryResponse>> =
        ApiResponse.success(orderService.getMyOrders(principal.userId, pageable))

    @GetMapping("/{orderId}")
    fun detail(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.getOrderDetail(principal.userId, orderId))

    @PostMapping("/{orderId}/cancel")
    fun cancel(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.cancelOrder(principal.userId, orderId), "주문이 취소되었습니다.")

    /** 하위 주문(판매자 단위) 부분 취소. 해당 판매자분만 취소·환불된다. */
    @PostMapping("/sub-orders/{subOrderId}/cancel")
    fun cancelSubOrder(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable subOrderId: Long,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(
            orderService.cancelSubOrder(principal.userId, subOrderId),
            "해당 판매자 주문이 취소되었습니다.",
        )
}
