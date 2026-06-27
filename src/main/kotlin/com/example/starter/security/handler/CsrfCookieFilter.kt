package com.example.starter.security.handler

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Spring Security 6 의 CSRF 토큰은 지연(deferred) 로딩이라, 토큰을 실제로 "사용"하기 전까지
 * [org.springframework.security.web.csrf.CookieCsrfTokenRepository] 가 XSRF-TOKEN 쿠키를 내려주지 않는다.
 * → SPA 클라이언트가 토큰을 받을 방법이 없어진다.
 *
 * 이 필터는 매 요청에서 토큰을 강제로 로드(`.token` 접근)하여 쿠키가 항상 응답에 실리도록 한다.
 * (CsrfFilter 이후에 등록한다.)
 */
class CsrfCookieFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val csrfToken = request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
        // 토큰 값을 읽어 렌더링을 트리거 → CookieCsrfTokenRepository 가 쿠키를 기록한다.
        csrfToken?.token
        filterChain.doFilter(request, response)
    }
}
