package com.example.starter.domain.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 자체 회원가입 요청.
 */
data class SignupRequest(
    @field:NotBlank
    @field:Email(message = "올바른 이메일 형식이 아닙니다.")
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
    val password: String,

    @field:NotBlank
    @field:Size(max = 50)
    val name: String,
)

/**
 * 자체 로그인 요청.
 */
data class LoginRequest(
    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    val password: String,
)
