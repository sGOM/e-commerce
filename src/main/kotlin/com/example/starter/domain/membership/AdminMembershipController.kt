package com.example.starter.domain.membership

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.membership.dto.AdminMembershipResponse
import com.example.starter.domain.membership.dto.AdminMembershipSearchCondition
import com.example.starter.domain.membership.dto.MembershipBillingHistoryResponse
import com.example.starter.domain.membership.dto.MembershipPolicyResponse
import com.example.starter.domain.membership.dto.UpdateMembershipPolicyRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 멤버십 운영 API (`ROLE_ADMIN`). 구독 현황 검색, 결제 이력 조회, 정책 관리,
 * 정기결제 배치 수동 트리거([MembershipBillingScheduler] 미가동 환경에서도 운영 가능).
 */
@RestController
@RequestMapping("/api/admin/memberships")
class AdminMembershipController(
    private val membershipBillingService: MembershipBillingService,
    private val membershipPolicyService: MembershipPolicyService,
) {

    @GetMapping
    fun search(
        condition: AdminMembershipSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<AdminMembershipResponse>> =
        ApiResponse.success(membershipBillingService.search(condition, pageable))

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<AdminMembershipResponse> =
        ApiResponse.success(membershipBillingService.getDetailForAdmin(id))

    @GetMapping("/{id}/billing-histories")
    fun getBillingHistories(@PathVariable id: Long): ApiResponse<List<MembershipBillingHistoryResponse>> =
        ApiResponse.success(membershipBillingService.getHistories(id))

    /** 정기결제 배치 수동 실행(스케줄러 비활성 환경 대비). */
    @PostMapping("/billing/run")
    fun runBilling(): ApiResponse<MembershipBillingRunResult> =
        ApiResponse.success(membershipBillingService.runDueBilling(), "정기결제 배치를 실행했습니다.")

    @GetMapping("/policy")
    fun getPolicy(): ApiResponse<MembershipPolicyResponse> =
        ApiResponse.success(membershipPolicyService.getPolicy())

    @PatchMapping("/policy")
    fun updatePolicy(@RequestBody @Valid request: UpdateMembershipPolicyRequest): ApiResponse<MembershipPolicyResponse> =
        ApiResponse.success(membershipPolicyService.update(request), "멤버십 정책을 변경했습니다.")
}
