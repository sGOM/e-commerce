package com.example.starter.domain.settlement

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.settlement.dto.SettlementResponse
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 판매자 정산 조회 API (`ROLE_SELLER`). 본인 상점의 정산 내역만 조회한다.
 */
@RestController
@RequestMapping("/api/seller/settlements")
class SellerSettlementController(
    private val settlementService: SettlementService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<SettlementResponse>> =
        ApiResponse.success(settlementService.getSellerSettlements(principal.userId))
}
