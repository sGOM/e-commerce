package com.example.starter.domain.catalog

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.catalog.dto.CategoryResponse
import com.example.starter.domain.catalog.dto.CreateCategoryRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
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
    fun create(@RequestBody @Valid request: CreateCategoryRequest): ApiResponse<CategoryResponse> =
        ApiResponse.success(adminCategoryService.create(request), "카테고리를 등록했습니다.")
}
