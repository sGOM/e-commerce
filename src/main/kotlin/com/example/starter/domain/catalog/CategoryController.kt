package com.example.starter.domain.catalog

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.catalog.dto.CategoryResponse
import com.example.starter.domain.catalog.repository.CategoryRepository
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 고객용 카테고리 조회 API. 인증 불필요(공개) — 상품 검색의 카테고리 필터에 사용한다.
 */
@RestController
@RequestMapping("/api/categories")
class CategoryController(
    private val categoryRepository: CategoryRepository,
) {

    @GetMapping
    fun list(): ApiResponse<List<CategoryResponse>> {
        val sort = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))
        return ApiResponse.success(categoryRepository.findAll(sort).map { CategoryResponse.from(it) })
    }
}
