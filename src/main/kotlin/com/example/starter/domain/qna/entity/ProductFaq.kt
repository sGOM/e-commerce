package com.example.starter.domain.qna.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/** 판매자가 정리해 공개하는 상품별 자주 묻는 질문(AC5). */
@Entity
@Table(name = "product_faqs")
class ProductFaq(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(nullable = false, length = 500)
    var question: String,

    @Column(nullable = false, length = 2000)
    var answer: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
