package com.example.starter.common.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * 요청 단위 감사 로그. 추가 전용(append-only)이라 수정 시각은 두지 않는다.
 *
 * [payload] 는 PostgreSQL `jsonb` 컬럼으로 저장되어(쿼리/마스킹 메타 포함),
 * 관리자 화면에서 JSONB 연산자로 유연하게 검색할 수 있다.
 */
@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Column(name = "user_id")
    val userId: Long?,

    @Column(nullable = false, length = 10)
    val method: String,

    @Column(nullable = false, length = 2048)
    val uri: String,

    @Column(length = 45)
    val ip: String?,

    @Column(name = "user_agent", length = 512)
    val userAgent: String?,

    @Column(name = "status_code", nullable = false)
    val statusCode: Int,

    @Column(name = "duration_ms", nullable = false)
    val durationMs: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val payload: String?,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
