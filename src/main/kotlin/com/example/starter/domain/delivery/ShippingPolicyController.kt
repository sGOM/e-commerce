package com.example.starter.domain.delivery

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.delivery.dto.ShippingPolicyResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 기본 배송비 공개 조회 — 체크아웃 미리보기용. 최종 금액은 주문 생성 시 서버가 계산한다. */
@RestController
class ShippingPolicyController(
    private val shippingPolicyService: ShippingPolicyService,
) {

    @GetMapping("/api/shipping-policy")
    fun get(): ApiResponse<ShippingPolicyResponse> = ApiResponse.success(shippingPolicyService.getPolicy())
}
