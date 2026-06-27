package com.example.starter.common.audit

import com.example.starter.security.oauth.CustomOAuth2User
import com.example.starter.security.userdetails.CustomUserDetails
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * 모든 요청을 캡처하여 감사 로그 항목을 만들고 비동기로 저장한다.
 *
 * Spring Security 체인 내부(인가 이후)에 등록되어 [SecurityContextHolder] 의 사용자 정보를
 * 안전하게 읽는다. 본문은 [ContentCachingRequestWrapper] 로 캐시 후 민감 키를 마스킹한다.
 */
class AuditLogFilter(
    private val auditLogService: AuditLogService,
    private val properties: AuditProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)
    private val pathMatcher = AntPathMatcher()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!properties.enabled || isExcluded(request.requestURI)) {
            filterChain.doFilter(request, response)
            return
        }

        val start = System.nanoTime()
        val wrappedReq = ContentCachingRequestWrapper(request)
        val wrappedRes = ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(wrappedReq, wrappedRes)
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            runCatching {
                auditLogService.saveAsync(buildEntry(wrappedReq, wrappedRes, durationMs))
            }.onFailure { log.warn("감사 로그 기록 실패: {}", it.message) }
            // 캐시된 응답 본문을 실제 응답으로 반드시 복사
            wrappedRes.copyBodyToResponse()
        }
    }

    private fun buildEntry(
        req: ContentCachingRequestWrapper,
        res: ContentCachingResponseWrapper,
        durationMs: Long,
    ): AuditLogEntry =
        AuditLogEntry(
            userId = currentUserId(),
            method = req.method,
            uri = req.requestURI.take(2048),
            ip = clientIp(req),
            userAgent = req.getHeader("User-Agent")?.take(512),
            statusCode = res.status,
            durationMs = durationMs,
            payload = buildPayload(req),
        )

    private fun currentUserId(): Long? =
        when (val principal = SecurityContextHolder.getContext().authentication?.principal) {
            is CustomUserDetails -> principal.userId
            is CustomOAuth2User -> principal.userId
            else -> null
        }

    private fun clientIp(req: HttpServletRequest): String? =
        req.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()?.ifBlank { null }
            ?: req.remoteAddr

    private fun buildPayload(req: ContentCachingRequestWrapper): String? {
        val payload = linkedMapOf<String, Any?>()
        req.queryString?.takeIf { it.isNotBlank() }?.let { payload["query"] = it }
        extractBody(req)?.let { payload["body"] = it }
        return if (payload.isEmpty()) null else objectMapper.writeValueAsString(payload)
    }

    private fun extractBody(req: ContentCachingRequestWrapper): Any? {
        val bytes = req.contentAsByteArray
        if (bytes.isEmpty()) return null
        val isJson = req.contentType?.contains("json", ignoreCase = true) == true
        if (isJson) {
            runCatching { mask(objectMapper.readValue(bytes, Any::class.java)) }
                .onSuccess { return it }
        }
        // JSON 이 아니거나 파싱 실패 → 길이 제한 문자열
        return String(bytes, Charsets.UTF_8).take(properties.maxBodyLength)
    }

    /** 민감 키 값을 재귀적으로 마스킹 */
    private fun mask(node: Any?): Any? = when (node) {
        is Map<*, *> -> node.entries.associate { (k, v) ->
            k.toString() to if (isSensitive(k.toString())) MASKED else mask(v)
        }
        is List<*> -> node.map { mask(it) }
        else -> node
    }

    private fun isSensitive(key: String): Boolean =
        properties.maskKeys.any { it.equals(key, ignoreCase = true) }

    private fun isExcluded(uri: String): Boolean =
        properties.excludePaths.any { pathMatcher.match(it, uri) }

    private companion object {
        const val MASKED = "***"
    }
}
