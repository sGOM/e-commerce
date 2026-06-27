package com.example.starter.common.audit

import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 감사 로그를 **비동기**로 저장한다(요청 응답 지연 최소화).
 * 저장 실패가 본 요청에 영향을 주지 않도록 별도 스레드/트랜잭션에서 처리한다.
 */
@Service
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
) {

    @Async("auditExecutor")
    @Transactional
    fun saveAsync(entry: AuditLogEntry) {
        auditLogRepository.save(entry.toEntity())
    }
}

/**
 * 필터 스레드에서 만들어 비동기 저장으로 넘기는 불변 스냅샷.
 * (요청/응답 객체가 아닌 원시값만 전달 → 스레드 경계 안전)
 */
data class AuditLogEntry(
    val userId: Long?,
    val method: String,
    val uri: String,
    val ip: String?,
    val userAgent: String?,
    val statusCode: Int,
    val durationMs: Long,
    val payload: String?,
) {
    fun toEntity(): AuditLog =
        AuditLog(
            userId = userId,
            method = method,
            uri = uri,
            ip = ip,
            userAgent = userAgent,
            statusCode = statusCode,
            durationMs = durationMs,
            payload = payload,
        )
}
