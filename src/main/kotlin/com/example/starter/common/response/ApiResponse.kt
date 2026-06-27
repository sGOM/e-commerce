package com.example.starter.common.response

import com.example.starter.common.exception.ErrorCode
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * 모든 REST 응답의 공통 표준 형태.
 *
 * 성공: `{ success: true, code: "SUCCESS", message, data }`
 * 실패: `{ success: false, code: "AUTH-001", message, errors? }`
 *
 * null 필드는 직렬화에서 제외한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: T? = null,
    val errors: List<FieldErrorDetail>? = null,
    val timestamp: Instant = Instant.now(),
) {
    companion object {
        private const val SUCCESS_CODE = "SUCCESS"
        private const val SUCCESS_MESSAGE = "요청이 정상 처리되었습니다."

        fun <T> success(data: T, message: String = SUCCESS_MESSAGE): ApiResponse<T> =
            ApiResponse(success = true, code = SUCCESS_CODE, message = message, data = data)

        fun success(message: String = SUCCESS_MESSAGE): ApiResponse<Unit> =
            ApiResponse(success = true, code = SUCCESS_CODE, message = message)

        fun error(
            errorCode: ErrorCode,
            message: String = errorCode.message,
            errors: List<FieldErrorDetail>? = null,
        ): ApiResponse<Unit> =
            ApiResponse(success = false, code = errorCode.code, message = message, errors = errors)
    }
}

/**
 * 검증 실패 시 필드 단위 상세.
 */
data class FieldErrorDetail(
    val field: String,
    val value: String?,
    val reason: String,
)
