package com.example.starter.domain.catalog.repository

import com.example.starter.domain.catalog.entity.Category
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, Long>, KotlinJdslJpqlExecutor {
    fun existsByParentId(parentId: Long): Boolean
}
