package com.example.starter.security.userdetails

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.entity.UserStatus
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * 인증 주체. [User] 엔티티를 Spring Security 의 [UserDetails] 로 감싼다.
 * authority 는 역할명(ROLE_*)과 권한명을 모두 포함한다.
 *
 * 소셜 로그인 주체([com.example.starter.security.oauth.CustomOAuth2User])가 이 클래스를 상속하므로,
 * 컨트롤러는 로그인 방식과 무관하게 `@AuthenticationPrincipal CustomUserDetails` 로 주체를 받는다.
 */
open class CustomUserDetails(
    val user: User,
) : UserDetails {

    val userId: Long
        get() = requireNotNull(user.id) { "영속화되지 않은 User 입니다." }

    override fun getAuthorities(): Collection<GrantedAuthority> =
        user.authorities().map { SimpleGrantedAuthority(it) }

    // 소셜 전용 계정은 비밀번호가 없다. 폼 로그인 시 어떤 입력과도 매칭되지 않도록 빈 문자열 반환.
    override fun getPassword(): String = user.password ?: ""

    override fun getUsername(): String = user.email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = user.status != UserStatus.LOCKED

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = user.status.canLogin
}
