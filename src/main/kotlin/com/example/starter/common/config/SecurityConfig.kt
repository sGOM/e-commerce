package com.example.starter.common.config

import com.example.starter.common.audit.AuditLogFilter
import com.example.starter.common.audit.AuditLogService
import com.example.starter.common.audit.AuditProperties
import com.example.starter.security.handler.CsrfCookieFilter
import com.example.starter.security.handler.RestAccessDeniedHandler
import com.example.starter.security.handler.RestAuthenticationEntryPoint
import com.example.starter.security.oauth.CustomOAuth2UserService
import com.example.starter.security.oauth.OAuth2LoginFailureHandler
import com.example.starter.security.oauth.OAuth2LoginSuccessHandler
import com.example.starter.security.userdetails.CustomUserDetailsService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.http.HttpMethod
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler

/**
 * 세션 기반 인증 + 기본 보안 하드닝.
 *
 * - 인증은 컨트롤러([com.example.starter.domain.auth])에서 [AuthenticationManager] 로 수행 후
 *   [SecurityContextRepository] 를 통해 세션에 저장한다.
 * - CSRF: SPA 친화적인 쿠키 토큰 방식(XSRF-TOKEN 쿠키 / X-XSRF-TOKEN 헤더).
 * - 인증/인가 실패는 [com.example.starter.security.handler] 가 JSON 으로 응답.
 * - 메서드 보안(@PreAuthorize) 활성화.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val userDetailsService: CustomUserDetailsService,
    private val authenticationEntryPoint: RestAuthenticationEntryPoint,
    private val accessDeniedHandler: RestAccessDeniedHandler,
    private val oAuth2UserService: CustomOAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val oAuth2LoginFailureHandler: OAuth2LoginFailureHandler,
    private val auditLogService: AuditLogService,
    private val auditProperties: AuditProperties,
    private val objectMapper: ObjectMapper,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        // {bcrypt} 등 알고리즘 식별자를 저장 → 추후 알고리즘 교체/업그레이드 용이
        PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun authenticationManager(passwordEncoder: PasswordEncoder): AuthenticationManager {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return ProviderManager(provider)
    }

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
        clientRegistrationRepository: ObjectProvider<ClientRegistrationRepository>,
    ): SecurityFilterChain {
        // OAuth2 클라이언트 등록정보가 있을 때만(=시크릿이 설정됐을 때만) 소셜 로그인을 켠다.
        val oauthEnabled = clientRegistrationRepository.ifAvailable != null
        http {
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                // BREACH 보호 + SPA 호환을 위한 표준 핸들러
                csrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
                // PG 웹훅은 서버 간 호출이라 CSRF 토큰이 없다. 내용은 PG 재조회로 검증한다(PaymentWebhookService).
                ignoringRequestMatchers("/api/payments/webhook/**")
            }
            // 지연 로딩된 CSRF 토큰을 매 요청에서 강제 렌더링 → XSRF-TOKEN 쿠키 항상 발급
            addFilterAfter<CsrfFilter>(CsrfCookieFilter())
            // 감사 로그: 인가 이후에 두어 SecurityContext(사용자)를 안전하게 읽는다
            addFilterAfter<AuthorizationFilter>(AuditLogFilter(auditLogService, auditProperties, objectMapper))
            authorizeHttpRequests {
                authorize("/api/auth/signup", permitAll)
                authorize("/api/auth/login", permitAll)
                // 비밀번호 분실 재설정(메일 토큰) - 비로그인 경로
                authorize("/api/auth/password-reset/**", permitAll)
                authorize("/actuator/health", permitAll)
                // 메트릭 스크레이프(무인증). 운영은 management 포트를 분리해 외부에 노출하지 않는다(application-prod.yml)
                authorize("/actuator/prometheus", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize("/login/oauth2/**", permitAll)
                authorize("/error", permitAll)
                // API 문서(개발용, prod 프로필에서는 springdoc 자체를 끈다)
                authorize("/v3/api-docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                // 재입고 알림 신청/취소는 회원 전용(AC3) — 아래 /api/products/** permitAll 보다 먼저 매칭되어야 한다
                authorize("/api/products/options/*/restock-alerts", authenticated)
                // 상품 탐색은 공개(게스트 허용). 조회 전용이므로 전체 허용(쓰기 경로는 /api/seller, /api/admin)
                authorize("/api/products", permitAll)
                authorize("/api/products/**", permitAll)
                // 카테고리 조회는 공개(상품 검색 필터용)
                authorize("/api/categories", permitAll)
                authorize("/api/categories/**", permitAll)
                // 컬렉션(기획전) 조회는 공개(홈/탐색 노출용, 편성은 /api/admin/collections)
                authorize("/api/collections", permitAll)
                authorize("/api/collections/**", permitAll)
                // 타임딜(한정특가) 조회는 공개(홈/상세 노출용). 신청은 /api/seller/flash-sales, /api/admin/flash-sales
                authorize("/api/flash-sales", permitAll)
                authorize("/api/flash-sales/**", permitAll)
                // 배송 슬롯 조회는 공개(체크아웃 전 게스트도 조회). 개설/지역 관리는 /api/admin/delivery-*
                authorize("/api/delivery-slots", permitAll)
                authorize("/api/delivery-slots/**", permitAll)
                // 게스트 장바구니 계산/검증(localStorage 동반, 무상태) — 비회원 허용
                authorize("/api/cart/guest", permitAll)
                // 게스트 주문 생성/조회 — 비회원 허용(조회는 주문번호+연락처로 검증)
                authorize("/api/orders/guest", permitAll)
                authorize("/api/orders/guest/lookup", permitAll)
                authorize("/api/payments/guest", permitAll)
                authorize("/api/payments/webhook/**", permitAll)
                // 선물 수령자 플로우 — 비회원 허용(토큰이 유일한 인가 수단, `docs/planning/gift-order.md` §4)
                authorize("/api/gift/**", permitAll)
                // 업로드 이미지 조회는 공개(상품/리뷰 이미지). 업로드 자체는 아래 anyRequest 로 회원 전용
                authorize(HttpMethod.GET, "/api/uploads/*", permitAll)
                authorize("/api/admin/**", hasRole("ADMIN"))
                // 입점 신청은 ROLE_SELLER 가 아직 없는 일반 회원이 수행한다(나머지 셀러 API보다 먼저 매칭)
                authorize("/api/seller/apply", authenticated)
                authorize("/api/seller/**", hasRole("SELLER"))
                authorize(anyRequest, authenticated)
            }
            securityContext {
                this.securityContextRepository = securityContextRepository
            }
            sessionManagement {
                sessionCreationPolicy = SessionCreationPolicy.IF_REQUIRED
                sessionConcurrency {
                    maximumSessions = 1
                    maxSessionsPreventsLogin = false
                }
            }
            exceptionHandling {
                authenticationEntryPoint = this@SecurityConfig.authenticationEntryPoint
                accessDeniedHandler = this@SecurityConfig.accessDeniedHandler
            }
            // 소셜 로그인 (등록정보가 있을 때만 활성화)
            if (oauthEnabled) {
                oauth2Login {
                    userInfoEndpoint {
                        userService = oAuth2UserService
                    }
                    authenticationSuccessHandler = oAuth2LoginSuccessHandler
                    authenticationFailureHandler = oAuth2LoginFailureHandler
                }
            }
            // 폼/베이직 로그인 비활성화 — 로그인은 JSON 컨트롤러로 처리
            formLogin { disable() }
            httpBasic { disable() }
            logout { disable() }
        }
        return http.build()
    }
}
