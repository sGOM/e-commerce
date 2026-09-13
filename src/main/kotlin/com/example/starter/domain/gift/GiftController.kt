package com.example.starter.domain.gift

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.gift.dto.GiftClaimRequest
import com.example.starter.domain.gift.dto.GiftClaimResponse
import com.example.starter.domain.gift.dto.GiftPreviewResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 선물 수령자용 공개 API — 인증 불필요(비회원 접근 지원, `docs/planning/gift-order.md` §4).
 * 토큰([token])이 유일한 인가 수단이다.
 */
@RestController
@RequestMapping("/api/gift")
class GiftController(
    private val giftClaimService: GiftClaimService,
) {

    /** 선물 미리보기(메시지/보낸사람/상품 요약, AC6). */
    @GetMapping("/{token}")
    fun preview(@PathVariable token: String): ApiResponse<GiftPreviewResponse> =
        ApiResponse.success(giftClaimService.getPreview(token))

    /** 배송지 입력 및 수락(AC7). 성공하면 1회성으로 재사용 불가(AC8). */
    @PostMapping("/{token}/claim")
    fun claim(
        @PathVariable token: String,
        @RequestBody @Valid request: GiftClaimRequest,
    ): ApiResponse<GiftClaimResponse> =
        ApiResponse.success(giftClaimService.claim(token, request), "배송지를 등록했습니다. 곧 배송이 시작됩니다.")
}
