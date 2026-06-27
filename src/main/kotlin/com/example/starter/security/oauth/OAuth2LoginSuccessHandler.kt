package com.example.starter.security.oauth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * 소셜 로그인 성공 후 프론트엔드로 리다이렉트한다.
 * 인증 자체는 oauth2Login 필터가 세션에 저장하므로 여기서는 이동만 담당한다.
 */
@Component
class OAuth2LoginSuccessHandler(
    @Value("\${app.oauth2.success-redirect-uri:/}") private val successRedirectUri: String,
) : SimpleUrlAuthenticationSuccessHandler() {

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        redirectStrategy.sendRedirect(request, response, successRedirectUri)
    }
}
