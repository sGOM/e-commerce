package com.example.starter.security.oauth

import com.example.starter.domain.user.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

/**
 * 소셜 로그인 인증 주체. 연동된 [User] 의 권한을 그대로 노출한다.
 * [getName] 은 제공자 내 고유 id(providerId)를 반환한다.
 */
class CustomOAuth2User(
    val user: User,
    private val attributes: Map<String, Any>,
    private val providerId: String,
) : OAuth2User {

    val userId: Long
        get() = requireNotNull(user.id)

    override fun getName(): String = providerId

    override fun getAttributes(): Map<String, Any> = attributes

    override fun getAuthorities(): Collection<GrantedAuthority> =
        user.authorities().map { SimpleGrantedAuthority(it) }
}
