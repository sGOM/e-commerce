package com.example.starter.domain.admin

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.AdminUserResponse
import com.example.starter.domain.admin.dto.GrantRoleRequest
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.admin.dto.UpdateUserStatusRequest
import com.example.starter.domain.admin.dto.UserSearchCondition
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 사용자 관리 API. URL 규칙(`/api/admin` 하위)에 더해 메서드 보안으로 이중 방어한다.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserController(
    private val adminUserService: AdminUserService,
) {

    @GetMapping
    fun search(
        condition: UserSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<AdminUserResponse>> =
        ApiResponse.success(adminUserService.search(condition, pageable))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ApiResponse<AdminUserResponse> =
        ApiResponse.success(adminUserService.get(id))

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateUserStatusRequest,
    ): ApiResponse<AdminUserResponse> =
        ApiResponse.success(adminUserService.changeStatus(id, request.status), "상태가 변경되었습니다.")

    @PostMapping("/{id}/roles")
    fun grantRole(
        @PathVariable id: Long,
        @RequestBody @Valid request: GrantRoleRequest,
    ): ApiResponse<AdminUserResponse> =
        ApiResponse.success(adminUserService.grantRole(id, request.role), "역할이 부여되었습니다.")

    @DeleteMapping("/{id}/roles/{role}")
    fun revokeRole(
        @PathVariable id: Long,
        @PathVariable role: String,
    ): ApiResponse<AdminUserResponse> =
        ApiResponse.success(adminUserService.revokeRole(id, role), "역할이 회수되었습니다.")
}
