package com.example.starter.security.oauth

import com.example.starter.domain.user.entity.OAuthProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.Instant

/** GitHub 이메일 조회 계약 테스트(외부 통신 없이 MockRestServiceServer). */
class GithubEmailEnricherTest {

    private val info = OAuthUserInfo(OAuthProvider.GITHUB, providerId = "1", email = null, name = "octocat")

    private val userRequest = OAuth2UserRequest(
        CommonOAuth2Provider.GITHUB.getBuilder("github").clientId("id").clientSecret("secret").build(),
        OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "tok", Instant.now(), Instant.now().plusSeconds(60)),
    )

    private fun enricherRespondingWith(body: String?): GithubEmailEnricher {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val expectation = server.expect(requestTo("https://api.github.com/user/emails"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer tok"))
        if (body == null) expectation.andRespond(withServerError()) else expectation.andRespond(withSuccess(body, MediaType.APPLICATION_JSON))
        return GithubEmailEnricher(builder)
    }

    @Test
    fun `기본이면서 검증된 이메일을 쓴다`() {
        val enricher = enricherRespondingWith(
            """[{"email":"other@example.com","primary":false,"verified":true},
                {"email":"me@example.com","primary":true,"verified":true,"visibility":null}]""",
        )

        assertThat(enricher.enrich(info, userRequest).email).isEqualTo("me@example.com")
    }

    @Test
    fun `기본 이메일이 미검증이면 이메일 없음으로 둔다(미검증 이메일로 기존 계정에 연동하지 않는다)`() {
        val enricher = enricherRespondingWith("""[{"email":"me@example.com","primary":true,"verified":false}]""")

        assertThat(enricher.enrich(info, userRequest).email).isNull()
    }

    @Test
    fun `조회가 실패해도 예외 없이 이메일 없음으로 둔다`() {
        assertThat(enricherRespondingWith(null).enrich(info, userRequest)).isEqualTo(info)
    }
}
