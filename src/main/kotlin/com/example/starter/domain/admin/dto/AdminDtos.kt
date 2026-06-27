package com.example.starter.domain.admin.dto

import com.example.starter.common.audit.AuditLog
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.entity.UserStatus
import java.time.Instant

/** 사용자 검색 조건 (모두 선택적 → 동적 쿼리) */
data class UserSearchCondition(
    val keyword: String? = null, // 이메일/이름 부분 일치
    val status: UserStatus? = null,
)

/** 감사 로그 검색 조건 */
data class AuditLogSearchCondition(
    val userId: Long? = null,
    val method: String? = null,
    val statusCode: Int? = null,
    val uriKeyword: String? = null,
    val from: Instant? = null,
    val to: Instant? = null,
)

/** 사용자 상태 변경 요청 */
data class UpdateUserStatusRequest(
    val status: UserStatus,
)

/** 역할 부여 요청 */
data class GrantRoleRequest(
    val role: String,
)

data class AdminUserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val status: UserStatus,
    val roles: List<String>,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User) = AdminUserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            name = user.name,
            status = user.status,
            roles = user.roles.map { it.name }.sorted(),
            createdAt = user.createdAt,
        )
    }
}

data class AuditLogResponse(
    val id: Long,
    val userId: Long?,
    val method: String,
    val uri: String,
    val ip: String?,
    val statusCode: Int,
    val durationMs: Long,
    val payload: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(log: AuditLog) = AuditLogResponse(
            id = requireNotNull(log.id),
            userId = log.userId,
            method = log.method,
            uri = log.uri,
            ip = log.ip,
            statusCode = log.statusCode,
            durationMs = log.durationMs,
            payload = log.payload,
            createdAt = log.createdAt,
        )
    }
}

/** 페이지 응답 공통 래퍼 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    companion object {
        fun <S, T> of(page: org.springframework.data.domain.Page<S>, mapper: (S) -> T) = PageResponse(
            content = page.content.map(mapper),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
    }
}
