package com.example.starter.domain.delivery

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.delivery.dto.ShippingPolicyResponse
import com.example.starter.domain.delivery.dto.UpdateShippingPolicyRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 기본 배송비 정책 변경 (`ROLE_ADMIN`). */
@RestController
@RequestMapping("/api/admin/shipping-policy")
class AdminShippingPolicyController(
    private val shippingPolicyService: ShippingPolicyService,
) {

    @PatchMapping
    fun update(@RequestBody @Valid request: UpdateShippingPolicyRequest): ApiResponse<ShippingPolicyResponse> =
        ApiResponse.success(shippingPolicyService.update(request.baseFee!!), "배송비 정책을 변경했습니다.")
}
