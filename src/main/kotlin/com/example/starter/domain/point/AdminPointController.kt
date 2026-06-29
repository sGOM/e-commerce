package com.example.starter.domain.point

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.point.dto.PointExpireResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 포인트 운영 API (`ROLE_ADMIN`). 만료 도래 포인트 일괄 소멸 트리거.
 * (스케줄러로 주기 실행도 가능하며, 운영/테스트를 위해 수동 트리거를 제공한다.)
 */
@RestController
@RequestMapping("/api/admin/points")
class AdminPointController(
    private val pointService: PointService,
) {

    @PostMapping("/expire")
    fun expire(): ApiResponse<PointExpireResponse> =
        ApiResponse.success(PointExpireResponse(pointService.expireDuePoints()), "만료 포인트를 처리했습니다.")
}
