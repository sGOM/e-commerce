package com.example.starter.domain.admin

import com.example.starter.common.audit.AuditLog
import com.example.starter.common.audit.AuditLogRepository
import com.example.starter.domain.admin.dto.AuditLogResponse
import com.example.starter.domain.admin.dto.AuditLogSearchCondition
import com.example.starter.domain.admin.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자용 감사 로그 검색. 사용자/메서드/상태코드/URI/기간을 동적으로 조합한다(Kotlin JDSL).
 */
@Service
@Transactional(readOnly = true)
class AdminAuditService(
    private val auditLogRepository: AuditLogRepository,
) {

    fun search(condition: AuditLogSearchCondition, pageable: Pageable): PageResponse<AuditLogResponse> {
        val page = auditLogRepository.findPage(pageable) {
            select(entity(AuditLog::class))
                .from(entity(AuditLog::class))
                .whereAnd(
                    condition.userId?.let { path(AuditLog::userId).eq(it) },
                    condition.method?.let { path(AuditLog::method).eq(it) },
                    condition.statusCode?.let { path(AuditLog::statusCode).eq(it) },
                    condition.uriKeyword?.let { path(AuditLog::uri).like("%$it%") },
                    condition.from?.let { path(AuditLog::createdAt).greaterThanOrEqualTo(it) },
                    condition.to?.let { path(AuditLog::createdAt).lessThan(it) },
                )
                .orderBy(path(AuditLog::id).desc())
        }
        return PageResponse.of(page) { AuditLogResponse.from(it!!) }
    }
}
