package com.example.starter.domain.catalog.dto

import com.example.starter.domain.catalog.entity.Category
import jakarta.validation.constraints.NotBlank

/** 카테고리 등록/수정 요청(관리자) */
data class CategoryRequest(
    @field:NotBlank val name: String?,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
)

/** 카테고리 응답 */
data class CategoryResponse(
    val categoryId: Long,
    val name: String,
    val parentId: Long?,
    val sortOrder: Int,
) {
    companion object {
        fun from(category: Category) = CategoryResponse(
            categoryId = requireNotNull(category.id),
            name = category.name,
            parentId = category.parent?.id,
            sortOrder = category.sortOrder,
        )
    }
}
