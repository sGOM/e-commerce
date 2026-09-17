package com.example.starter.common.exception

import com.example.starter.common.response.ApiResponse
import com.example.starter.common.response.FieldErrorDetail
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.BindException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * 전역 예외 처리. 모든 예외를 [ApiResponse] 표준 형태로 변환한다.
 *
 * 인증 실패(401)는 Security 필터 단계에서 발생하므로
 * [com.example.starter.security.handler] 의 EntryPoint/Handler 가 별도로 처리한다.
 * 여기서는 디스패처 진입 이후 발생하는 예외를 다룬다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 의도된 비즈니스 예외 */
    @ExceptionHandler(BusinessException::class)
    fun handleBusiness(e: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("BusinessException: {} - {}", e.errorCode.code, e.message)
        return respond(e.errorCode, e.message)
    }

    /** @Valid 바디 검증 실패 */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val details = e.bindingResult.fieldErrors.map {
            FieldErrorDetail(
                field = it.field,
                value = it.rejectedValue?.toString(),
                reason = it.defaultMessage ?: "유효하지 않은 값입니다.",
            )
        }
        return respond(ErrorCode.INVALID_INPUT, errors = details)
    }

    /** @ModelAttribute / 폼 바인딩 검증 실패 */
    @ExceptionHandler(BindException::class)
    fun handleBind(e: BindException): ResponseEntity<ApiResponse<Unit>> {
        val details = e.bindingResult.fieldErrors.map {
            FieldErrorDetail(it.field, it.rejectedValue?.toString(), it.defaultMessage ?: "유효하지 않은 값입니다.")
        }
        return respond(ErrorCode.INVALID_INPUT, errors = details)
    }

    /** 요청 본문 파싱 실패(JSON 형식 오류 등) */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> {
        return respond(ErrorCode.MESSAGE_NOT_READABLE)
    }

    /** 허용되지 않은 HTTP 메서드 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(e: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Unit>> {
        return respond(ErrorCode.METHOD_NOT_ALLOWED)
    }

    /** 업로드 크기 초과(`spring.servlet.multipart.max-file-size`) */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleMaxUploadSize(e: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Unit>> {
        return respond(ErrorCode.INVALID_INPUT, "파일은 5MB 이하만 업로드할 수 있습니다.")
    }

    /**
     * 메서드 보안(@PreAuthorize) 등에서 발생하는 인가 실패.
     * 디스패처 진입 이후이므로 ControllerAdvice 에서 잡힌다.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ApiResponse<Unit>> {
        return respond(ErrorCode.ACCESS_DENIED)
    }

    /** 예상치 못한 모든 예외 */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("Unhandled exception", e)
        return respond(ErrorCode.INTERNAL_ERROR)
    }

    private fun respond(
        errorCode: ErrorCode,
        message: String = errorCode.message,
        errors: List<FieldErrorDetail>? = null,
    ): ResponseEntity<ApiResponse<Unit>> =
        ResponseEntity.status(errorCode.status)
            .body(ApiResponse.error(errorCode, message, errors))
}
