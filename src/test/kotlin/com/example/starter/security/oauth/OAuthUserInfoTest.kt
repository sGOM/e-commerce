package com.example.starter.security.oauth

import com.example.starter.common.exception.BusinessException
import com.example.starter.domain.user.entity.OAuthProvider
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class OAuthUserInfoTest {

    @Test
    fun `구글 속성을 정규화한다`() {
        val attr = mapOf("sub" to "g-123", "email" to "g@example.com", "name" to "구글유저")

        val info = OAuthUserInfo.of(OAuthProvider.GOOGLE, attr)

        assertThat(info.provider).isEqualTo(OAuthProvider.GOOGLE)
        assertThat(info.providerId).isEqualTo("g-123")
        assertThat(info.email).isEqualTo("g@example.com")
        assertThat(info.name).isEqualTo("구글유저")
    }

    @Test
    fun `네이버 속성(response 중첩)을 정규화한다`() {
        val attr = mapOf(
            "response" to mapOf("id" to "n-999", "email" to "n@example.com", "name" to "네이버유저"),
        )

        val info = OAuthUserInfo.of(OAuthProvider.NAVER, attr)

        assertThat(info.providerId).isEqualTo("n-999")
        assertThat(info.email).isEqualTo("n@example.com")
        assertThat(info.name).isEqualTo("네이버유저")
    }

    @Test
    fun `카카오 속성(kakao_account 중첩)을 정규화한다`() {
        val attr = mapOf(
            "id" to 4242L,
            "kakao_account" to mapOf(
                "email" to "k@example.com",
                "profile" to mapOf("nickname" to "카카오유저"),
            ),
        )

        val info = OAuthUserInfo.of(OAuthProvider.KAKAO, attr)

        assertThat(info.providerId).isEqualTo("4242")
        assertThat(info.email).isEqualTo("k@example.com")
        assertThat(info.name).isEqualTo("카카오유저")
    }

    @Test
    fun `이메일이 없으면 email 은 null 로 파싱된다`() {
        val attr = mapOf("id" to 1L, "kakao_account" to mapOf<String, Any>())

        val info = OAuthUserInfo.of(OAuthProvider.KAKAO, attr)

        assertThat(info.email).isNull()
        assertThat(info.name).isEqualTo("kakao_user")
    }

    @Test
    fun `필수 식별자가 없으면 예외`() {
        assertThatThrownBy { OAuthUserInfo.of(OAuthProvider.GOOGLE, emptyMap()) }
            .isInstanceOf(BusinessException::class.java)
    }
}
