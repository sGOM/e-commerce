package com.example.starter.domain.admin

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.AuditLogResponse
import com.example.starter.domain.admin.dto.AuditLogSearchCondition
import com.example.starter.domain.admin.dto.PageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 감사 로그 조회 API.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
class AdminAuditController(
    private val adminAuditService: AdminAuditService,
) {

    @GetMapping
    fun search(
        condition: AuditLogSearchCondition,
        @PageableDefault(size = 20, sort = ["id"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ApiResponse<PageResponse<AuditLogResponse>> =
        ApiResponse.success(adminAuditService.search(condition, pageable))
}
