package com.example.starter.security.handler

import com.example.starter.common.exception.ErrorCode
import com.example.starter.common.response.ApiResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * 미인증 상태로 보호 자원 접근 시 401 JSON 응답.
 * (Security 필터 단계에서 발생하므로 ControllerAdvice 가 잡지 못한다.)
 */
@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.writeApiError(objectMapper, ErrorCode.UNAUTHENTICATED)
    }
}

/** 공통: 에러 코드를 JSON 본문으로 직렬화한다. */
internal fun HttpServletResponse.writeApiError(objectMapper: ObjectMapper, errorCode: ErrorCode) {
    status = errorCode.status.value()
    contentType = MediaType.APPLICATION_JSON_VALUE
    characterEncoding = Charsets.UTF_8.name()
    objectMapper.writeValue(writer, ApiResponse.error(errorCode))
}
