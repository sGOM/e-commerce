package com.example.starter.domain.seller

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.seller.dto.SellerApproveRequest
import com.example.starter.domain.seller.dto.SellerResponse
import com.example.starter.domain.seller.entity.SellerStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 입점 심사 API (`ROLE_ADMIN`). 입점 신청 조회 및 승인/거절.
 */
@RestController
@RequestMapping("/api/admin/sellers")
class AdminSellerController(
    private val adminSellerService: AdminSellerService,
) {

    @GetMapping
    fun list(@RequestParam(required = false) status: SellerStatus?): ApiResponse<List<SellerResponse>> =
        ApiResponse.success(adminSellerService.search(status))

    @PatchMapping("/{sellerId}/approve")
    fun review(
        @PathVariable sellerId: Long,
        @RequestBody request: SellerApproveRequest,
    ): ApiResponse<SellerResponse> =
        ApiResponse.success(
            adminSellerService.review(sellerId, request.approved),
            if (request.approved) "입점을 승인했습니다." else "입점을 거절했습니다.",
        )
}
