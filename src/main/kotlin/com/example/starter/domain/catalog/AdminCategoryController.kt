package com.example.starter.domain.catalog

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.catalog.dto.CategoryRequest
import com.example.starter.domain.catalog.dto.CategoryResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 카테고리 API (`ROLE_ADMIN`).
 */
@RestController
@RequestMapping("/api/admin/categories")
class AdminCategoryController(
    private val adminCategoryService: AdminCategoryService,
) {

    @PostMapping
    fun create(@RequestBody @Valid request: CategoryRequest): ApiResponse<CategoryResponse> =
        ApiResponse.success(adminCategoryService.create(request), "카테고리를 등록했습니다.")

    @PutMapping("/{categoryId}")
    fun update(
        @PathVariable categoryId: Long,
        @RequestBody @Valid request: CategoryRequest,
    ): ApiResponse<CategoryResponse> =
        ApiResponse.success(adminCategoryService.update(categoryId, request), "카테고리를 수정했습니다.")

    @DeleteMapping("/{categoryId}")
    fun delete(@PathVariable categoryId: Long): ApiResponse<Unit> {
        adminCategoryService.delete(categoryId)
        return ApiResponse.success("카테고리를 삭제했습니다.")
    }
}
