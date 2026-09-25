package com.example.starter.domain.qna

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.qna.dto.AnswerInquiryRequest
import com.example.starter.domain.qna.dto.CreateInquiryRequest
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 상품 Q&A API. 경로별 인가는 SecurityConfig URL 규칙을 따른다:
 * 공개 FAQ(`/api/products` 하위 공개), 내 문의(`/api/me` 하위 회원), 판매자 답변·FAQ 관리(`/api/seller` 하위 ROLE_SELLER).
 */
@RestController
class ProductQnaController(
    private val qnaService: ProductQnaService,
) {

    @GetMapping("/api/products/{productId}/faqs")
    fun faqs(@PathVariable productId: Long): ApiResponse<List<FaqResponse>> = ApiResponse.success(qnaService.faqs(productId))

    @PostMapping("/api/me/inquiries")
    fun ask(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateInquiryRequest,
    ): ApiResponse<InquiryResponse> =
        ApiResponse.success(qnaService.ask(principal.userId, request.productId!!, request.question!!), "문의가 등록되었습니다.")

    @GetMapping("/api/me/inquiries")
    fun myInquiries(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) productId: Long?,
    ): ApiResponse<List<InquiryResponse>> = ApiResponse.success(qnaService.myInquiries(principal.userId, productId))

    @GetMapping("/api/seller/inquiries")
    fun sellerInquiries(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(required = false) answered: Boolean?,
    ): ApiResponse<List<InquiryResponse>> = ApiResponse.success(qnaService.sellerInquiries(principal.userId, answered))

    @PutMapping("/api/seller/inquiries/{inquiryId}/answer")
    fun answer(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable inquiryId: Long,
        @RequestBody @Valid request: AnswerInquiryRequest,
    ): ApiResponse<InquiryResponse> =
        ApiResponse.success(qnaService.answer(principal.userId, inquiryId, request.answer!!), "답변을 등록했습니다.")

    @PostMapping("/api/seller/products/{productId}/faqs")
    fun addFaq(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable productId: Long,
        @RequestBody @Valid request: SaveFaqRequest,
    ): ApiResponse<FaqResponse> = ApiResponse.success(qnaService.addFaq(principal.userId, productId, request), "FAQ 를 등록했습니다.")

    @PutMapping("/api/seller/faqs/{faqId}")
    fun updateFaq(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable faqId: Long,
        @RequestBody @Valid request: SaveFaqRequest,
    ): ApiResponse<FaqResponse> = ApiResponse.success(qnaService.updateFaq(principal.userId, faqId, request), "FAQ 를 수정했습니다.")

    @DeleteMapping("/api/seller/faqs/{faqId}")
    fun deleteFaq(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable faqId: Long,
    ): ApiResponse<Unit> {
        qnaService.deleteFaq(principal.userId, faqId)
        return ApiResponse.success("FAQ 를 삭제했습니다.")
    }
}
