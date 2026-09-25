package com.example.starter.domain.qna.dto

import com.example.starter.domain.qna.entity.ProductFaq
import com.example.starter.domain.qna.entity.ProductInquiry
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

/** 비밀 문의 작성 요청(회원) */
data class CreateInquiryRequest(
    @field:NotNull
    val productId: Long? = null,
    @field:NotBlank
    @field:Size(max = 1000)
    val question: String? = null,
)

/** 판매자 답변 요청 */
data class AnswerInquiryRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val answer: String? = null,
)

/** 문의 응답(작성자 본인·판매자에게만 내려간다) */
data class InquiryResponse(
    val inquiryId: Long,
    val productId: Long,
    val productName: String,
    val question: String,
    val answer: String?,
    val answeredAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun from(inquiry: ProductInquiry, productName: String) = InquiryResponse(
            inquiryId = requireNotNull(inquiry.id),
            productId = inquiry.productId,
            productName = productName,
            question = inquiry.question,
            answer = inquiry.answer,
            answeredAt = inquiry.answeredAt,
            createdAt = inquiry.createdAt,
        )
    }
}

/** FAQ 등록·수정 요청(판매자) */
data class SaveFaqRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val question: String? = null,
    @field:NotBlank
    @field:Size(max = 2000)
    val answer: String? = null,
    val sortOrder: Int = 0,
)

/** 공개 FAQ 응답 */
data class FaqResponse(
    val faqId: Long,
    val question: String,
    val answer: String,
    val sortOrder: Int,
) {
    companion object {
        fun from(faq: ProductFaq) = FaqResponse(requireNotNull(faq.id), faq.question, faq.answer, faq.sortOrder)
    }
}
