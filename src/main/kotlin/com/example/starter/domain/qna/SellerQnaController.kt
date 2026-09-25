package com.example.starter.domain.qna

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.qna.dto.AnswerInquiryRequest
import com.example.starter.domain.qna.dto.FaqResponse
import com.example.starter.domain.qna.dto.InquiryResponse
import com.example.starter.domain.qna.dto.SaveFaqRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 판매자 문의 답변·FAQ 관리 (`ROLE_SELLER`) — 자기 상점 상품만. */
@RestController
@RequestMapping("/api/seller")
class SellerQnaController(
    private val qnaService: ProductQnaService,
) {

    @GetMapping("/inquiries")
    fun inquiries(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) answered: Boolean?,
    ): ApiResponse<List<InquiryResponse>> = ApiResponse.success(qnaService.sellerInquiries(principal.userId, answered))

    @PutMapping("/inquiries/{inquiryId}/answer")
    fun answer(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable inquiryId: Long,
        @RequestBody @Valid request: AnswerInquiryRequest,
    ): ApiResponse<InquiryResponse> =
        ApiResponse.success(qnaService.answer(principal.userId, inquiryId, request.answer!!), "답변을 등록했습니다.")

    @PostMapping("/products/{productId}/faqs")
    fun addFaq(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable productId: Long,
        @RequestBody @Valid request: SaveFaqRequest,
    ): ApiResponse<FaqResponse> = ApiResponse.success(qnaService.addFaq(principal.userId, productId, request), "FAQ 를 등록했습니다.")

    @PutMapping("/faqs/{faqId}")
    fun updateFaq(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable faqId: Long,
        @RequestBody @Valid request: SaveFaqRequest,
    ): ApiResponse<FaqResponse> = ApiResponse.success(qnaService.updateFaq(principal.userId, faqId, request), "FAQ 를 수정했습니다.")

    @DeleteMapping("/faqs/{faqId}")
    fun deleteFaq(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable faqId: Long,
    ): ApiResponse<Unit> {
        qnaService.deleteFaq(principal.userId, faqId)
        return ApiResponse.success("FAQ 를 삭제했습니다.")
    }
}
