package com.example.starter.security.oauth

import com.example.starter.domain.user.entity.OAuthProvider
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * GitHub 는 이메일을 비공개로 둘 수 있어 `/user` 에 없을 수 있다. `user:email` 스코프로 `/user/emails` 를 조회해
 * **기본(primary)이면서 검증된** 이메일만 쓴다 — 이메일로 기존 계정에 연동하므로 미검증 이메일은 계정 탈취 경로가 된다.
 * https://docs.github.com/en/rest/users/emails#list-email-addresses-for-the-authenticated-user
 *
 * 조회 실패 시 null 로 둔다: 이미 연동된 사용자는 (provider, providerId) 로 찾으므로 영향이 없고,
 * 신규 사용자는 공통 흐름에서 `email_not_provided` 로 거부된다.
 */
@Component
class GithubEmailEnricher(
    restClientBuilder: RestClient.Builder = RestClient.builder(),
) : OAuthUserInfoEnricher {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient = restClientBuilder.build()

    override val provider = OAuthProvider.GITHUB

    override fun enrich(info: OAuthUserInfo, userRequest: OAuth2UserRequest): OAuthUserInfo {
        val emailsUri = userRequest.clientRegistration.providerDetails.userInfoEndpoint.uri.trimEnd('/') + "/emails"
        val emails = try {
            restClient.get().uri(emailsUri)
                .header("Authorization", "Bearer ${userRequest.accessToken.tokenValue}")
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(object : ParameterizedTypeReference<List<GithubEmail>>() {})
                .orEmpty()
        } catch (e: RestClientException) {
            log.warn("GitHub 이메일 조회 실패: {}", e.message)
            emptyList()
        }
        return info.copy(email = emails.firstOrNull { it.primary && it.verified }?.email)
    }

    private data class GithubEmail(val email: String, val primary: Boolean = false, val verified: Boolean = false)
}
