package com.example.starter.domain.seller

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.seller.dto.SellerSubOrderResponse
import com.example.starter.domain.seller.dto.ShipRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 판매자 주문 처리 API (`ROLE_SELLER`). 본인 판매분 SubOrder 조회 및 송장 등록.
 */
@RestController
@RequestMapping("/api/seller/orders")
class SellerOrderController(
    private val sellerOrderService: SellerOrderService,
) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) status: SubOrderStatus?,
    ): ApiResponse<List<SellerSubOrderResponse>> =
        ApiResponse.success(sellerOrderService.getMySubOrders(principal.userId, status))

    @PostMapping("/{subOrderId}/ship")
    fun ship(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable subOrderId: Long,
        @RequestBody @Valid request: ShipRequest,
    ): ApiResponse<SellerSubOrderResponse> =
        ApiResponse.success(
            sellerOrderService.ship(principal.userId, subOrderId, request.courier!!, request.trackingNumber!!),
            "송장을 등록하고 발송 처리했습니다.",
        )
}
