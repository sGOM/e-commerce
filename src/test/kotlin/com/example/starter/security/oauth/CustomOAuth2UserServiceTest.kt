package com.example.starter.security.oauth

import com.example.starter.domain.auth.AuthService
import com.example.starter.domain.user.entity.OAuthAccount
import com.example.starter.domain.user.entity.OAuthProvider
import com.example.starter.domain.user.entity.Role
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.entity.UserStatus
import com.example.starter.domain.user.repository.OAuthAccountRepository
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

class CustomOAuth2UserServiceTest {

    private val userRepository = mockk<UserRepository>()
    private val oAuthAccountRepository = mockk<OAuthAccountRepository>()
    private val roleRepository = mockk<RoleRepository>()

    private val service = CustomOAuth2UserService(userRepository, oAuthAccountRepository, roleRepository)

    private fun info(email: String? = "u@example.com") =
        OAuthUserInfo(OAuthProvider.GOOGLE, providerId = "g-1", email = email, name = "유저")

    @Test
    fun `이미 연동된 소셜 계정이면 해당 사용자를 반환하고 새 연동을 만들지 않는다`() {
        val linked = User(email = "u@example.com", password = null, name = "유저")
        every { oAuthAccountRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "g-1") } returns
            OAuthAccount(user = linked, provider = OAuthProvider.GOOGLE, providerId = "g-1", email = "u@example.com")
        every { userRepository.findWithRolesByEmail("u@example.com") } returns linked

        val result = service.resolveUser(OAuthProvider.GOOGLE, info())

        assertThat(result).isSameAs(linked)
        verify(exactly = 0) { oAuthAccountRepository.save(any()) }
    }

    @Test
    fun `연동은 없지만 같은 이메일의 자체 계정이 있으면 연동만 추가한다`() {
        val existing = User(email = "u@example.com", password = "{bcrypt}xxx", name = "기존유저")
        every { oAuthAccountRepository.findByProviderAndProviderId(any(), any()) } returns null
        every { userRepository.findWithRolesByEmail("u@example.com") } returns existing
        every { oAuthAccountRepository.save(any()) } answers { firstArg() }

        val result = service.resolveUser(OAuthProvider.GOOGLE, info())

        assertThat(result).isSameAs(existing)
        val saved = slot<OAuthAccount>()
        verify(exactly = 1) { oAuthAccountRepository.save(capture(saved)) }
        assertThat(saved.captured.user).isSameAs(existing)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `연동도 계정도 없으면 신규 사용자를 자동 생성하고 연동한다`() {
        every { oAuthAccountRepository.findByProviderAndProviderId(any(), any()) } returns null
        every { userRepository.findWithRolesByEmail("new@example.com") } returns null
        every { roleRepository.findByName(AuthService.DEFAULT_ROLE) } returns Role(name = "ROLE_USER")
        every { userRepository.save(any()) } answers { firstArg() }
        every { oAuthAccountRepository.save(any()) } answers { firstArg() }

        val result = service.resolveUser(OAuthProvider.GOOGLE, info(email = "new@example.com"))

        assertThat(result.email).isEqualTo("new@example.com")
        assertThat(result.isSocialOnly).isTrue()
        assertThat(result.roles.map { it.name }).contains("ROLE_USER")
        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { oAuthAccountRepository.save(any()) }
    }

    @Test
    fun `소셜 계정에서 이메일을 못 받으면 인증 예외를 던진다`() {
        every { oAuthAccountRepository.findByProviderAndProviderId(any(), any()) } returns null

        assertThatThrownBy { service.resolveUser(OAuthProvider.GOOGLE, info(email = null)) }
            .isInstanceOf(OAuth2AuthenticationException::class.java)
    }

    @Test
    fun `탈퇴하거나 잠긴 계정은 소셜 로그인도 거부한다`() {
        val withdrawn = User(email = "u@example.com", password = null, name = "유저", status = UserStatus.WITHDRAWN)
        every { oAuthAccountRepository.findByProviderAndProviderId(OAuthProvider.GOOGLE, "g-1") } returns
            OAuthAccount(user = withdrawn, provider = OAuthProvider.GOOGLE, providerId = "g-1", email = "u@example.com")
        every { userRepository.findWithRolesByEmail("u@example.com") } returns withdrawn

        assertThatThrownBy { service.resolveUser(OAuthProvider.GOOGLE, info()) }
            .isInstanceOf(OAuth2AuthenticationException::class.java)
    }
}
