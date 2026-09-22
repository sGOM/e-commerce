package com.example.starter.domain.auth

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.auth.dto.PasswordResetConfirmRequest
import com.example.starter.domain.auth.dto.PasswordResetRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 비밀번호 분실 재설정 API (비로그인 공개 경로).
 */
@RestController
@RequestMapping("/api/auth/password-reset")
class PasswordResetController(
    private val passwordResetService: PasswordResetService,
) {

    @PostMapping("/request")
    fun request(@RequestBody @Valid request: PasswordResetRequest): ApiResponse<Unit> {
        passwordResetService.request(request.email)
        // 가입 여부와 무관하게 같은 응답(계정 열거 방지)
        return ApiResponse.success(Unit, "가입된 주소라면 재설정 메일을 보냈습니다.")
    }

    @PostMapping("/confirm")
    fun confirm(@RequestBody @Valid request: PasswordResetConfirmRequest): ApiResponse<Unit> {
        passwordResetService.confirm(request.token, request.newPassword)
        return ApiResponse.success(Unit, "비밀번호가 변경되었습니다.")
    }
}
