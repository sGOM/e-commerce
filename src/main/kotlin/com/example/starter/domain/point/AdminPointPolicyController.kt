package com.example.starter.domain.point

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.point.dto.PointPolicyResponse
import com.example.starter.domain.point.dto.UpdatePointPolicyRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 포인트 적립 정책 관리 API (`ROLE_ADMIN`). 적립률을 런타임 조회/변경한다.
 */
@RestController
@RequestMapping("/api/admin/point-policy")
class AdminPointPolicyController(
    private val pointPolicyService: PointPolicyService,
) {

    @GetMapping
    fun get(): ApiResponse<PointPolicyResponse> =
        ApiResponse.success(pointPolicyService.getPolicy())

    @PatchMapping
    fun update(@RequestBody @Valid request: UpdatePointPolicyRequest): ApiResponse<PointPolicyResponse> =
        ApiResponse.success(
            pointPolicyService.update(request.earnRateBp, request.expiryDays),
            "포인트 정책을 변경했습니다.",
        )
}
