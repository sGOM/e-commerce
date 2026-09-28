package com.example.starter.domain.user.entity

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode

/**
 * 지원하는 소셜 로그인 제공자. enum 이름은 Spring Security 의 `registrationId`(소문자)와 매핑된다.
 * 추가 절차: 항목 추가 → [com.example.starter.security.oauth.OAuthUserInfo.of] 분기(컴파일러가 강제)
 * → 속성만으로 부족하면 [com.example.starter.security.oauth.OAuthUserInfoEnricher] 빈 → `application-oauth-<id>.yml`.
 */
enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO,
    GITHUB,
    ;

    companion object {
        fun from(registrationId: String): OAuthProvider =
            entries.firstOrNull { it.name.equals(registrationId, ignoreCase = true) }
                ?: throw BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, "지원하지 않는 제공자: $registrationId")
    }
}
