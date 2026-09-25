package com.example.starter.domain.qna.repository

import com.example.starter.domain.qna.entity.ProductFaq
import org.springframework.data.jpa.repository.JpaRepository

interface ProductFaqRepository : JpaRepository<ProductFaq, Long> {
    fun findByProductIdOrderBySortOrderAscIdAsc(productId: Long): List<ProductFaq>
}
