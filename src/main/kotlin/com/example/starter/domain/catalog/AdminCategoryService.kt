package com.example.starter.domain.catalog

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.catalog.dto.CategoryResponse
import com.example.starter.domain.catalog.dto.CreateCategoryRequest
import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.repository.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 카테고리 관리. 계층형 카테고리를 등록한다(부모 지정 시 트리 구성).
 */
@Service
@Transactional(readOnly = true)
class AdminCategoryService(
    private val categoryRepository: CategoryRepository,
) {

    @Transactional
    fun create(request: CreateCategoryRequest): CategoryResponse {
        val parent = request.parentId?.let {
            categoryRepository.findById(it)
                .orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }
        }
        val category = categoryRepository.save(
            Category(name = request.name!!, parent = parent, sortOrder = request.sortOrder),
        )
        return CategoryResponse.from(category)
    }
}
