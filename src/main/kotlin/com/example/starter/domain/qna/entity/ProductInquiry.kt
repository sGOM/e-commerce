package com.example.starter.domain.qna.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/** 상품 비밀 문의. 작성자와 상품 판매자만 볼 수 있다(`docs/planning/product-qna.md` AC2). */
@Entity
@Table(name = "product_inquiries")
class ProductInquiry(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(nullable = false, length = 1000)
    val question: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(length = 2000)
    var answer: String? = null
        protected set

    @Column(name = "answered_at")
    var answeredAt: Instant? = null
        protected set

    /** 답변 작성·수정(AC4). 고치면 최종 답변 시각이 갱신된다. */
    fun answer(text: String, now: Instant) {
        answer = text
        answeredAt = now
    }
}
