package com.example.starter.security.oauth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * 소셜 로그인 실패 시 프론트엔드로 에러 정보와 함께 리다이렉트한다.
 */
@Component
class OAuth2LoginFailureHandler(
    @Value("\${app.oauth2.failure-redirect-uri:/login}") private val failureRedirectUri: String,
) : SimpleUrlAuthenticationFailureHandler() {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val target = UriComponentsBuilder.fromUriString(failureRedirectUri)
            .queryParam("error", URLEncoder.encode(exception.message ?: "oauth2_login_failed", StandardCharsets.UTF_8))
            .build()
            .toUriString()
        redirectStrategy.sendRedirect(request, response, target)
    }
}
