package com.example.starter.domain.qna.repository

import com.example.starter.domain.qna.entity.ProductInquiry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductInquiryRepository : JpaRepository<ProductInquiry, Long> {

    fun findByUserIdOrderByIdDesc(userId: Long): List<ProductInquiry>

    fun findByUserIdAndProductIdOrderByIdDesc(userId: Long, productId: Long): List<ProductInquiry>

    /** 판매자 상점 상품에 달린 문의. [answered] 가 null 이면 전체, true/false 면 답변 여부로 거른다. */
    @Query(
        """
        select i from ProductInquiry i, Product p
        where p.id = i.productId and p.seller.id = :sellerId
          and (:answered is null or (:answered = true and i.answeredAt is not null) or (:answered = false and i.answeredAt is null))
        order by i.id desc
        """,
    )
    fun findBySellerId(@Param("sellerId") sellerId: Long, @Param("answered") answered: Boolean?): List<ProductInquiry>
}
