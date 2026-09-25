package com.example.starter.domain.qna

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

/** 상품 Q&A(ROADMAP 2.4, `docs/planning/product-qna.md`): 비밀 문의 + 공개 FAQ. */
@AutoConfigureMockMvc
@Transactional
class ProductQnaIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var notificationRepository: NotificationRepository

    private fun member(email: String) = CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** 판매자 계정과 그 상점의 상품을 만들고 (판매자 주체, 상품 id) 반환 */
    private fun sellerWithProduct(key: String): Pair<CustomUserDetails, Long> {
        val u = User(email = "$key@seller.com", password = "{noop}x", name = key)
        u.grantRole(roleRepository.findByName("ROLE_SELLER")!!)
        val sellerUser = userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = sellerUser.id!!, storeName = key, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$key", basePrice = 10_000, status = ProductStatus.ON_SALE)
        product.addOption(ProductOption(name = "기본", sku = "QNA-$key", additionalPrice = 0).apply { assignInventory(Inventory(quantity = 5, reserved = 0)) })
        return CustomUserDetails(sellerUser) to productRepository.save(product).id!!
    }

    private fun ask(asker: CustomUserDetails, productId: Long, question: String = "사이즈가 정사이즈인가요?"): Long {
        val res = mockMvc.post("/api/me/inquiries") {
            with(user(asker)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId":$productId,"question":"$question"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""inquiryId":(\d+)""").find(res)!!.groupValues[1].toLong()
    }

    @Test
    fun `회원이 문의하면 판매자에게 알림이 가고, 판매자 답변은 작성자에게 알림과 함께 보인다`() {
        val (seller, productId) = sellerWithProduct("qna1")
        val asker = member("qna-asker-1@example.com")

        val inquiryId = ask(asker, productId)
        assertTrue(notificationRepository.findAll().any { it.userId == seller.userId && it.type == NotificationType.PRODUCT_QNA })

        mockMvc.get("/api/seller/inquiries?answered=false") { with(user(seller)) }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].inquiryId") { value(inquiryId) }
            jsonPath("$.data[0].question") { value("사이즈가 정사이즈인가요?") }
        }
        mockMvc.put("/api/seller/inquiries/$inquiryId/answer") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"정사이즈입니다."}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/me/inquiries?productId=$productId") { with(user(asker)) }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].answer") { value("정사이즈입니다.") }
            jsonPath("$.data[0].answeredAt") { exists() }
        }
        assertTrue(notificationRepository.findAll().any { it.userId == asker.userId && it.type == NotificationType.PRODUCT_QNA })
        mockMvc.get("/api/seller/inquiries?answered=false") { with(user(seller)) }.andExpect { jsonPath("$.data.length()") { value(0) } }
    }

    @Test
    fun `문의는 비밀이다 — 다른 회원 목록에 보이지 않고 다른 판매자는 답변할 수 없다`() {
        val (_, productId) = sellerWithProduct("qna2")
        val (otherSeller, _) = sellerWithProduct("qna2-other")
        val inquiryId = ask(member("qna-asker-2@example.com"), productId)

        mockMvc.get("/api/me/inquiries?productId=$productId") { with(user(member("qna-stranger@example.com"))) }
            .andExpect { jsonPath("$.data.length()") { value(0) } }
        mockMvc.get("/api/seller/inquiries") { with(user(otherSeller)) }
            .andExpect { jsonPath("$.data.length()") { value(0) } }
        mockMvc.put("/api/seller/inquiries/$inquiryId/answer") {
            with(user(otherSeller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"answer":"x"}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `게스트는 문의할 수 없고, 빈 질문은 거부된다`() {
        val (_, productId) = sellerWithProduct("qna3")

        mockMvc.post("/api/me/inquiries") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId":$productId,"question":"q"}"""
        }.andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/me/inquiries") {
            with(user(member("qna-asker-3@example.com"))); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId":$productId,"question":" "}"""
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `판매자가 등록한 FAQ 는 누구나 정렬 순서대로 보고, 판매자만 수정·삭제한다`() {
        val (seller, productId) = sellerWithProduct("qna4")
        val (otherSeller, _) = sellerWithProduct("qna4-other")
        fun addFaq(question: String, sortOrder: Int): Long {
            val res = mockMvc.post("/api/seller/products/$productId/faqs") {
                with(user(seller)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"question":"$question","answer":"답","sortOrder":$sortOrder}"""
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString
            return Regex(""""faqId":(\d+)""").find(res)!!.groupValues[1].toLong()
        }
        val second = addFaq("세탁 방법은?", 2)
        addFaq("배송은 얼마나 걸리나요?", 1)

        mockMvc.get("/api/products/$productId/faqs").andExpect {
            status { isOk() }
            jsonPath("$.data[0].question") { value("배송은 얼마나 걸리나요?") }
            jsonPath("$.data[1].question") { value("세탁 방법은?") }
        }
        mockMvc.delete("/api/seller/faqs/$second") { with(user(otherSeller)); with(csrf()) }.andExpect { status { isNotFound() } }
        mockMvc.put("/api/seller/faqs/$second") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"question":"세탁은 어떻게 하나요?","answer":"찬물 손세탁","sortOrder":2}"""
        }.andExpect { status { isOk() } }
        mockMvc.delete("/api/seller/faqs/$second") { with(user(seller)); with(csrf()) }.andExpect { status { isOk() } }
        mockMvc.get("/api/products/$productId/faqs").andExpect { jsonPath("$.data.length()") { value(1) } }
    }
}
