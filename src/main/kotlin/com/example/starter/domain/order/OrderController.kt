package com.example.starter.domain.order

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.gift.GiftOrderService
import com.example.starter.domain.gift.dto.GiftClaimResponse
import com.example.starter.domain.order.dto.ClaimGuestOrderRequest
import com.example.starter.domain.order.dto.CreateOrderRequest
import com.example.starter.domain.order.dto.GuestOrderLookupRequest
import com.example.starter.domain.order.dto.GuestOrderRequest
import com.example.starter.domain.order.dto.OrderResponse
import com.example.starter.domain.order.dto.OrderSummaryResponse
import com.example.starter.domain.order.dto.TrackingResponse
import com.example.starter.domain.order.tracking.DeliveryTrackingService
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
    private val giftOrderService: GiftOrderService,
    private val deliveryTrackingService: DeliveryTrackingService,
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

    /** 게스트 주문을 회원 계정에 연결(claim). 주문번호+연락처로 본인 확인 후 소유자를 채운다. */
    @PostMapping("/claim")
    fun claim(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: ClaimGuestOrderRequest,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(orderService.claimGuestOrder(principal.userId, request), "주문을 계정에 연결했습니다.")

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

    /** 하위 주문(판매자 단위) 수령 확인(구매확정). SHIPPED → DELIVERED. 이후 해당 항목에 리뷰를 쓸 수 있다. */
    /** 배송 조회(ROADMAP 6.2) — 본인 주문의 발송된 하위 주문만. */
    @GetMapping("/sub-orders/{subOrderId}/tracking")
    fun tracking(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable subOrderId: Long,
    ): ApiResponse<TrackingResponse> = ApiResponse.success(deliveryTrackingService.track(principal.userId, subOrderId))

    @PostMapping("/sub-orders/{subOrderId}/confirm-delivery")
    fun confirmDelivery(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable subOrderId: Long,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(
            orderService.confirmDelivery(principal.userId, subOrderId),
            "수령을 확인했습니다.",
        )

    /** 선물 링크 상태 조회(공유 링크 재확인/"보낸 선물" 상세, `docs/planning/gift-order.md`). */
    @GetMapping("/{orderId}/gift")
    fun giftStatus(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable orderId: Long,
    ): ApiResponse<GiftClaimResponse> =
        ApiResponse.success(giftOrderService.getStatus(principal.userId, orderId))

    /** 구매자의 선물 주문 취소(AC11 — 수락 전이면 전액 환불, 이후는 일반 취소 정책과 동일). */
    @PostMapping("/{orderId}/gift/cancel")
    fun cancelGift(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderResponse> =
        ApiResponse.success(giftOrderService.cancelByBuyer(principal.userId, orderId), "선물 주문이 취소되었습니다.")
}
