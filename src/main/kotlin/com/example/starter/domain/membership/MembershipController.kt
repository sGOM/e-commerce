package com.example.starter.domain.membership

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.membership.dto.MembershipBillingKeyResponse
import com.example.starter.domain.membership.dto.MembershipResponse
import com.example.starter.domain.membership.dto.RegisterBillingKeyRequest
import com.example.starter.domain.membership.dto.SubscribeMembershipRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 멤버십 API (인증 필요, 게스트 미지원). 카드 등록 → 구독 시작 → 조회/해지.
 */
@RestController
@RequestMapping("/api/me/membership")
class MembershipController(
    private val membershipService: MembershipService,
) {

    @PostMapping("/billing-key")
    fun registerBillingKey(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: RegisterBillingKeyRequest,
    ): ApiResponse<MembershipBillingKeyResponse> =
        ApiResponse.success(membershipService.registerBillingKey(principal.userId, request), "카드가 등록되었습니다.")

    @PostMapping
    fun subscribe(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody(required = false) request: SubscribeMembershipRequest?,
    ): ApiResponse<MembershipResponse> =
        ApiResponse.success(
            membershipService.subscribe(principal.userId, (request ?: SubscribeMembershipRequest()).plan),
            "멤버십 구독이 시작되었습니다.",
        )

    @GetMapping
    fun getMy(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<MembershipResponse> =
        ApiResponse.success(membershipService.getMy(principal.userId))

    @DeleteMapping
    fun cancel(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<MembershipResponse> =
        ApiResponse.success(membershipService.cancel(principal.userId), "멤버십 해지가 예약되었습니다. 남은 이용 기간까지는 혜택이 유지됩니다.")
}
