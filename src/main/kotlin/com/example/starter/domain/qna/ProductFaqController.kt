package com.example.starter.domain.qna

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.qna.dto.FaqResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 상품 FAQ 공개 조회 — `/api/products` 하위라 인증 불필요. */
@RestController
@RequestMapping("/api/products/{productId}/faqs")
class ProductFaqController(
    private val qnaService: ProductQnaService,
) {

    @GetMapping
    fun list(@PathVariable productId: Long): ApiResponse<List<FaqResponse>> = ApiResponse.success(qnaService.faqs(productId))
}
