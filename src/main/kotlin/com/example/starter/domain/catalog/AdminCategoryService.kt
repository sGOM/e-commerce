package com.example.starter.domain.catalog

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.catalog.dto.CategoryRequest
import com.example.starter.domain.catalog.dto.CategoryResponse
import com.example.starter.domain.catalog.entity.Category
import com.example.starter.domain.catalog.repository.CategoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 카테고리 관리. 계층형 카테고리를 등록·수정·삭제한다(부모 지정 시 트리 구성).
 */
@Service
@Transactional(readOnly = true)
class AdminCategoryService(
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
) {

    @Transactional
    fun create(request: CategoryRequest): CategoryResponse {
        val category = categoryRepository.save(
            Category(name = request.name!!, parent = request.parentId?.let(::find), sortOrder = request.sortOrder),
        )
        return CategoryResponse.from(category)
    }

    @Transactional
    fun update(categoryId: Long, request: CategoryRequest): CategoryResponse {
        val category = find(categoryId)
        val parent = request.parentId?.let(::find)
        // 새 상위에서 루트까지 올라가며 자신을 만나면 순환 → 거부
        generateSequence(parent) { it.parent }.forEach {
            if (it.id == categoryId) throw BusinessException(ErrorCode.CATEGORY_INVALID_PARENT)
        }
        category.name = request.name!!
        category.parent = parent
        category.sortOrder = request.sortOrder
        return CategoryResponse.from(category)
    }

    /** 상품이 옮겨갈 곳을 관리자가 정하도록, 하위 카테고리나 상품이 남아 있으면 삭제하지 않는다. */
    @Transactional
    fun delete(categoryId: Long) {
        val category = find(categoryId)
        if (categoryRepository.existsByParentId(categoryId) || productRepository.existsByCategoryId(categoryId)) {
            throw BusinessException(ErrorCode.CATEGORY_IN_USE)
        }
        categoryRepository.delete(category)
    }

    private fun find(id: Long): Category =
        categoryRepository.findById(id).orElseThrow { BusinessException(ErrorCode.CATEGORY_NOT_FOUND) }
}
