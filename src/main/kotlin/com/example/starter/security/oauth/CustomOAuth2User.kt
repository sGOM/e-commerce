package com.example.starter.security.oauth

import com.example.starter.domain.user.entity.User
import com.example.starter.security.userdetails.CustomUserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * 소셜 로그인 인증 주체. 연동된 [User] 의 권한을 그대로 노출한다.
 * [getName] 은 제공자 내 고유 id(providerId)를 반환한다.
 *
 * [CustomUserDetails] 를 상속해, 회원 API 들의 `@AuthenticationPrincipal CustomUserDetails` 주입이
 * 소셜 로그인 사용자에게도 동작하게 한다(상속 전에는 null 이 주입돼 500 이었다).
 */
class CustomOAuth2User(
    user: User,
    private val attributes: Map<String, Any>,
    private val providerId: String,
) : CustomUserDetails(user), OAuth2User {

    override fun getName(): String = providerId

    override fun getAttributes(): Map<String, Any> = attributes
}
