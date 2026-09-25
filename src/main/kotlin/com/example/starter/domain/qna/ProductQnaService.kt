package com.example.starter.domain.qna

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.qna.dto.FaqResponse
import com.example.starter.domain.qna.dto.InquiryResponse
import com.example.starter.domain.qna.dto.SaveFaqRequest
import com.example.starter.domain.qna.entity.ProductFaq
import com.example.starter.domain.qna.entity.ProductInquiry
import com.example.starter.domain.qna.repository.ProductFaqRepository
import com.example.starter.domain.qna.repository.ProductInquiryRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.repository.SellerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 상품 Q&A(ROADMAP 2.4, `docs/planning/product-qna.md`). 고객 문의는 비밀 — 작성자 본인과 상품 판매자만 조회하며,
 * 다른 사람이 접근하면 존재를 숨긴다(404). 공개는 판매자가 정리하는 FAQ 로만 한다.
 */
@Service
@Transactional(readOnly = true)
class ProductQnaService(
    private val inquiryRepository: ProductInquiryRepository,
    private val faqRepository: ProductFaqRepository,
    private val productRepository: ProductRepository,
    private val sellerRepository: SellerRepository,
    private val notificationService: NotificationService,
) {

    @Transactional
    fun ask(userId: Long, productId: Long, question: String): InquiryResponse {
        val product = productRepository.findById(productId).orElseThrow { BusinessException(ErrorCode.PRODUCT_NOT_FOUND) }
        val inquiry = inquiryRepository.save(ProductInquiry(productId = productId, userId = userId, question = question.trim()))
        notificationService.notify(
            userId = product.seller.userId,
            type = NotificationType.PRODUCT_QNA,
            title = "새 상품 문의가 도착했습니다",
            body = "'${product.name}' 에 답변을 기다리는 문의가 있습니다.",
            linkUrl = "/seller/inquiries",
        )
        return InquiryResponse.from(inquiry, product.name)
    }

    fun myInquiries(userId: Long, productId: Long?): List<InquiryResponse> {
        val inquiries = if (productId == null) {
            inquiryRepository.findByUserIdOrderByIdDesc(userId)
        } else {
            inquiryRepository.findByUserIdAndProductIdOrderByIdDesc(userId, productId)
        }
        return withProductNames(inquiries)
    }

    fun sellerInquiries(sellerUserId: Long, answered: Boolean?): List<InquiryResponse> =
        withProductNames(inquiryRepository.findBySellerId(requireNotNull(seller(sellerUserId).id), answered))

    @Transactional
    fun answer(sellerUserId: Long, inquiryId: Long, answer: String): InquiryResponse {
        val inquiry = inquiryRepository.findById(inquiryId).orElseThrow { BusinessException(ErrorCode.INQUIRY_NOT_FOUND) }
        val product = ownedProduct(sellerUserId, inquiry.productId, ErrorCode.INQUIRY_NOT_FOUND)
        inquiry.answer(answer.trim(), Instant.now())
        notificationService.notify(
            userId = inquiry.userId,
            type = NotificationType.PRODUCT_QNA,
            title = "문의에 답변이 등록되었습니다",
            body = "'${product.name}' 문의에 판매자가 답변했습니다.",
            linkUrl = "/products/${product.id}",
        )
        return InquiryResponse.from(inquiry, product.name)
    }

    fun faqs(productId: Long): List<FaqResponse> =
        faqRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).map { FaqResponse.from(it) }

    @Transactional
    fun addFaq(sellerUserId: Long, productId: Long, request: SaveFaqRequest): FaqResponse {
        ownedProduct(sellerUserId, productId, ErrorCode.PRODUCT_NOT_FOUND)
        val faq = ProductFaq(
            productId = productId,
            question = request.question!!.trim(),
            answer = request.answer!!.trim(),
            sortOrder = request.sortOrder,
        )
        return FaqResponse.from(faqRepository.save(faq))
    }

    @Transactional
    fun updateFaq(sellerUserId: Long, faqId: Long, request: SaveFaqRequest): FaqResponse {
        val faq = ownedFaq(sellerUserId, faqId)
        faq.question = request.question!!.trim()
        faq.answer = request.answer!!.trim()
        faq.sortOrder = request.sortOrder
        return FaqResponse.from(faq)
    }

    @Transactional
    fun deleteFaq(sellerUserId: Long, faqId: Long) = faqRepository.delete(ownedFaq(sellerUserId, faqId))

    private fun withProductNames(inquiries: List<ProductInquiry>): List<InquiryResponse> {
        val names = productRepository.findAllById(inquiries.map { it.productId }.toSet()).associate { it.id to it.name }
        return inquiries.map { InquiryResponse.from(it, names[it.productId].orEmpty()) }
    }

    private fun seller(userId: Long): Seller =
        sellerRepository.findByUserId(userId) ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)

    /** 판매자 본인 상점 상품만 — 아니면 [notFound] 로 존재를 숨긴다. */
    private fun ownedProduct(sellerUserId: Long, productId: Long, notFound: ErrorCode): Product {
        val product = productRepository.findById(productId).orElseThrow { BusinessException(notFound) }
        if (product.seller.id != seller(sellerUserId).id) throw BusinessException(notFound)
        return product
    }

    private fun ownedFaq(sellerUserId: Long, faqId: Long): ProductFaq {
        val faq = faqRepository.findById(faqId).orElseThrow { BusinessException(ErrorCode.FAQ_NOT_FOUND) }
        ownedProduct(sellerUserId, faq.productId, ErrorCode.FAQ_NOT_FOUND)
        return faq
    }
}
