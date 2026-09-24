package com.example.starter.security.oauth

import com.example.starter.domain.auth.AuthService
import com.example.starter.domain.user.entity.OAuthAccount
import com.example.starter.domain.user.entity.OAuthProvider
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.OAuthAccountRepository
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 소셜 로그인 시 사용자 조회/연동/자동가입을 처리한다.
 *
 * 1. (provider, providerId) 로 기존 연동 계정 조회 → 있으면 그 사용자로 로그인
 * 2. 없으면 이메일로 자체 계정 조회:
 *    - 있으면 → 해당 계정에 소셜 연동 추가 (자체 로그인과 통합)
 *    - 없으면 → 신규 사용자 자동 생성 후 연동
 */
@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository,
    private val oAuthAccountRepository: OAuthAccountRepository,
    private val roleRepository: RoleRepository,
) : DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oAuth2User = super.loadUser(userRequest)
        val provider = OAuthProvider.from(userRequest.clientRegistration.registrationId)
        val info = OAuthUserInfo.of(provider, oAuth2User.attributes)

        val user = resolveUser(provider, info)
        return CustomOAuth2User(user, oAuth2User.attributes, info.providerId)
    }

    internal fun resolveUser(provider: OAuthProvider, info: OAuthUserInfo): User {
        // 1) 이미 연동된 소셜 계정
        oAuthAccountRepository.findByProviderAndProviderId(provider, info.providerId)?.let { account ->
            return userRepository.findWithRolesByEmail(account.user.email)
                ?: account.user.also { it.roles.size } // 방어적 초기화
        }

        // 소셜 계정에서 이메일을 못 받으면 자체 계정과 연동할 수 없다.
        val email = info.email
            ?: throw OAuth2AuthenticationException(
                OAuth2Error("email_not_provided", "소셜 계정에서 이메일을 제공받지 못했습니다.", null),
            )

        // 2) 이메일로 기존 계정 연동 / 3) 없으면 자동 가입
        val user = userRepository.findWithRolesByEmail(email) ?: createUser(email, info.name)
        oAuthAccountRepository.save(OAuthAccount(user = user, provider = provider, providerId = info.providerId, email = email))
        return user
    }

    private fun createUser(email: String, name: String): User {
        val role = roleRepository.findByName(AuthService.DEFAULT_ROLE)
            ?: throw IllegalStateException("기본 역할(${AuthService.DEFAULT_ROLE})이 설정되지 않았습니다.")
        // 소셜 전용 계정 → 비밀번호 없음
        val user = User(email = email, password = null, name = name)
        user.grantRole(role)
        return userRepository.save(user)
    }
}
