package com.example.starter.common.exception

/**
 * 도메인 규칙 위반 등 의도된 비즈니스 예외.
 * [errorCode]가 HTTP 상태와 응답 코드/메시지를 결정한다.
 * [message]를 넘기면 기본 메시지를 덮어쓴다.
 */
open class BusinessException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
