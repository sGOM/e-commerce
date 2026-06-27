package com.example.starter.common.audit

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 감사 로그 동작 설정 (`app.audit.*`).
 */
@ConfigurationProperties(prefix = "app.audit")
data class AuditProperties(
    /** 감사 로그 활성화 여부 */
    val enabled: Boolean = true,

    /** 감사에서 제외할 경로 패턴(Ant 스타일) */
    val excludePaths: List<String> = listOf(
        "/actuator/**",
        "/error",
        "/favicon.ico",
    ),

    /** 페이로드에서 마스킹할 키(대소문자 무시). 비밀번호/토큰 등 민감정보 보호 */
    val maskKeys: Set<String> = setOf(
        "password",
        "passwordHash",
        "password_hash",
        "secret",
        "token",
        "accessToken",
        "refreshToken",
        "authorization",
    ),

    /** 기록할 요청 본문 최대 길이(바이트). 초과 시 절단 */
    val maxBodyLength: Int = 2000,
)
