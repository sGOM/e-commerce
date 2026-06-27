package com.example.starter.common.exception

import org.springframework.http.HttpStatus

/**
 * 애플리케이션 전역 에러 코드.
 *
 * 코드 체계: `{도메인}-{번호}` (예: AUTH-001). HTTP 상태와 기본 메시지를 한곳에서 관리한다.
 * 새 에러는 도메인별 구간에 추가한다.
 */
enum class ErrorCode(
    val status: HttpStatus,
    val code: String,
    val message: String,
) {
    // 공통 (COMMON)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON-001", "서버 내부 오류가 발생했습니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-002", "입력값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON-003", "허용되지 않은 HTTP 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON-004", "요청한 리소스를 찾을 수 없습니다."),
    MESSAGE_NOT_READABLE(HttpStatus.BAD_REQUEST, "COMMON-005", "요청 본문을 해석할 수 없습니다."),

    // 인증/인가 (AUTH)
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "AUTH-001", "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-002", "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH-003", "이메일 또는 비밀번호가 올바르지 않습니다."),

    // 사용자 (USER)
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "USER-002", "이미 사용 중인 이메일입니다."),
    USER_INACTIVE(HttpStatus.FORBIDDEN, "USER-003", "비활성화된 계정입니다."),

    // 소셜 로그인 (OAUTH)
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "OAUTH-001", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_EMAIL_NOT_PROVIDED(HttpStatus.BAD_REQUEST, "OAUTH-002", "소셜 계정에서 이메일을 제공받지 못했습니다."),
}
