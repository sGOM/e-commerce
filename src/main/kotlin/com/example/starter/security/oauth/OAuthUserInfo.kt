package com.example.starter.security.oauth

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.user.entity.OAuthProvider

/**
 * 제공자별로 제각각인 사용자 속성을 공통 형태로 정규화한 값.
 *
 * - Google: 평탄 구조 (`sub`, `email`, `name`)
 * - Naver : `response` 하위에 중첩 (`id`, `email`, `name`)
 * - Kakao : `id`(최상위) + `kakao_account.email` + `kakao_account.profile.nickname`
 * - GitHub: `id`(숫자) + `name`(없으면 `login`). 이메일은 비공개일 수 있어 [GithubEmailEnricher] 가 채운다.
 */
data class OAuthUserInfo(
    val provider: OAuthProvider,
    val providerId: String,
    val email: String?,
    val name: String,
) {
    companion object {
        fun of(provider: OAuthProvider, attributes: Map<String, Any>): OAuthUserInfo =
            when (provider) {
                OAuthProvider.GOOGLE -> google(attributes)
                OAuthProvider.NAVER -> naver(attributes)
                OAuthProvider.KAKAO -> kakao(attributes)
                OAuthProvider.GITHUB -> github(attributes)
            }

        private fun google(attr: Map<String, Any>) = OAuthUserInfo(
            provider = OAuthProvider.GOOGLE,
            providerId = attr.requireString("sub"),
            email = attr["email"] as? String,
            name = attr["name"] as? String ?: "google_user",
        )

        @Suppress("UNCHECKED_CAST")
        private fun naver(attr: Map<String, Any>): OAuthUserInfo {
            val response = attr["response"] as? Map<String, Any>
                ?: throw oauthError("Naver 응답에 response 가 없습니다.")
            return OAuthUserInfo(
                provider = OAuthProvider.NAVER,
                providerId = response.requireString("id"),
                email = response["email"] as? String,
                name = response["name"] as? String ?: "naver_user",
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun kakao(attr: Map<String, Any>): OAuthUserInfo {
            val id = attr["id"]?.toString() ?: throw oauthError("Kakao 응답에 id 가 없습니다.")
            val account = attr["kakao_account"] as? Map<String, Any>
            val profile = account?.get("profile") as? Map<String, Any>
            return OAuthUserInfo(
                provider = OAuthProvider.KAKAO,
                providerId = id,
                email = account?.get("email") as? String,
                name = profile?.get("nickname") as? String ?: "kakao_user",
            )
        }

        // /user 의 email 은 쓰지 않는다 — 이메일로 기존 계정에 연동하므로 검증 여부를 알 수 있는 /user/emails 만 신뢰한다.
        private fun github(attr: Map<String, Any>) = OAuthUserInfo(
            provider = OAuthProvider.GITHUB,
            providerId = attr.requireString("id"),
            email = null,
            name = attr["name"] as? String ?: attr["login"] as? String ?: "github_user",
        )

        private fun Map<String, Any>.requireString(key: String): String =
            this[key]?.toString() ?: throw oauthError("필수 속성 누락: $key")

        private fun oauthError(message: String) =
            BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER, message)
    }
}
