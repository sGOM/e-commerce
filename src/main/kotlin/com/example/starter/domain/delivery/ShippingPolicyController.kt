package com.example.starter.domain.delivery

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.delivery.dto.ShippingPolicyResponse
import com.example.starter.domain.delivery.dto.UpdateShippingPolicyRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 배송비 정책 API. 조회는 공개(체크아웃 미리보기용 — 최종 금액은 주문 생성 시 서버가 계산), 변경은 관리자.
 */
@RestController
class ShippingPolicyController(
    private val shippingPolicyService: ShippingPolicyService,
) {

    @GetMapping("/api/shipping-policy")
    fun get(): ApiResponse<ShippingPolicyResponse> = ApiResponse.success(shippingPolicyService.getPolicy())

    @PatchMapping("/api/admin/shipping-policy")
    fun update(@RequestBody @Valid request: UpdateShippingPolicyRequest): ApiResponse<ShippingPolicyResponse> =
        ApiResponse.success(shippingPolicyService.update(request.baseFee!!), "배송비 정책을 변경했습니다.")
}
