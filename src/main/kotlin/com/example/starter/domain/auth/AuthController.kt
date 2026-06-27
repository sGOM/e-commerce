package com.example.starter.domain.auth

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.auth.dto.LoginRequest
import com.example.starter.domain.auth.dto.SignupRequest
import com.example.starter.domain.user.dto.UserResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 자체 로그인 인증 API.
 *
 * 로그인은 [AuthenticationManager] 로 검증 후 [SecurityContextRepository] 를 통해
 * [SecurityContext] 를 세션에 저장한다(세션 기반 인증).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@RequestBody @Valid request: SignupRequest): ApiResponse<UserResponse> {
        val user = authService.signup(request)
        return ApiResponse.success(UserResponse.from(user), "회원가입이 완료되었습니다.")
    }

    @PostMapping("/login")
    fun login(
        @RequestBody @Valid request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ApiResponse<UserResponse> {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email, request.password),
            )
        } catch (e: AuthenticationException) {
            // 계정 존재/잠금 여부를 노출하지 않도록 단일 메시지로 통일
            throw BusinessException(ErrorCode.INVALID_CREDENTIALS)
        }

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        val principal = authentication.principal as CustomUserDetails
        return ApiResponse.success(UserResponse.from(principal.user), "로그인되었습니다.")
    }

    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ApiResponse<Unit> {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        return ApiResponse.success("로그아웃되었습니다.")
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<UserResponse> =
        ApiResponse.success(UserResponse.from(principal.user))
}
