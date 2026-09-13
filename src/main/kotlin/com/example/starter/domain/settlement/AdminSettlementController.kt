package com.example.starter.domain.settlement

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.settlement.dto.SettlementPolicyResponse
import com.example.starter.domain.settlement.dto.SettlementResponse
import com.example.starter.domain.settlement.dto.UpdateSettlementPolicyRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 정산 API (`ROLE_ADMIN`). 정산 생성·지급 및 수수료 정책 관리.
 */
@RestController
@RequestMapping("/api/admin/settlements")
class AdminSettlementController(
    private val settlementService: SettlementService,
    private val settlementPolicyService: SettlementPolicyService,
) {

    /** 미정산 대상을 판매자별로 모아 정산서를 생성한다. */
    @PostMapping
    fun generate(): ApiResponse<List<SettlementResponse>> =
        ApiResponse.success(settlementService.generate(), "정산서를 생성했습니다.")

    @PatchMapping("/{settlementId}/pay")
    fun pay(@PathVariable settlementId: Long): ApiResponse<SettlementResponse> =
        ApiResponse.success(settlementService.pay(settlementId), "지급 완료 처리했습니다.")

    @GetMapping("/policy")
    fun getPolicy(): ApiResponse<SettlementPolicyResponse> =
        ApiResponse.success(settlementPolicyService.getPolicy())

    @PatchMapping("/policy")
    fun updatePolicy(@RequestBody @Valid request: UpdateSettlementPolicyRequest): ApiResponse<SettlementPolicyResponse> =
        ApiResponse.success(settlementPolicyService.update(request.commissionRateBp!!), "수수료율을 변경했습니다.")
}
