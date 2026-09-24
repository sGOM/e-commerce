package com.example.starter.domain.order

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.order.dto.OrderResponse
import com.example.starter.domain.order.dto.OrderSummaryResponse
import com.example.starter.domain.order.entity.OrderStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * 관리자 주문 운영 API (`ROLE_ADMIN`). 전체 주문 검색 및 환불 처리.
 */
@RestController
@RequestMapping("/api/admin/orders")
class AdminOrderController(
    private val adminOrderService: AdminOrderService,
    private val unpaidOrderExpiryService: UnpaidOrderExpiryService,
) {

    /** 미결제 주문 만료 배치 수동 실행(스케줄러 비활성 환경 대비). 만료 처리한 건수를 돌려준다. */
    @PostMapping("/expire-unpaid/run")
    fun expireUnpaid(): ApiResponse<Int> =
        ApiResponse.success(unpaidOrderExpiryService.expireUnpaid(), "미결제 주문 만료를 실행했습니다.")

    @GetMapping
    fun search(
        @RequestParam(required = false) status: OrderStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<OrderSummaryResponse>> =
        ApiResponse.success(adminOrderService.search(status, from, to, pageable))

    @PostMapping("/{orderId}/refund")
    fun refund(@PathVariable orderId: Long): ApiResponse<OrderResponse> =
        ApiResponse.success(adminOrderService.refund(orderId), "환불 처리했습니다.")
}
