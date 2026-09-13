package com.example.starter.domain.review

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.point.repository.PointAccountRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * 리뷰 작성 자격(배송완료) → 작성 → 적립 → 평점 재계산 → 중복 방지 → 신고/숨김 전체 플로우 검증.
 */
@AutoConfigureMockMvc
@Transactional
class ReviewIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var pointAccountRepository: PointAccountRepository
    @Autowired lateinit var em: EntityManager

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** 판매자(ROLE_SELLER) + 상품을 만들고 (sellerUserDetails, optionId) 를 돌려준다. */
    private fun seedSeller(sku: String): Pair<CustomUserDetails, Long> {
        val u = User(email = "$sku@seller.com", password = "{noop}x", name = sku)
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        val optionId = productRepository.save(product).options.first().id!!
        return CustomUserDetails(u) to optionId
    }

    /** 주문 생성 → 결제 → 판매자 발송 → 구매자 수령확인(DELIVERED) 까지 마치고 (orderItemId, subOrderId, productId) 를 반환. */
    private fun deliverOneItem(buyer: CustomUserDetails, seller: CustomUserDetails, optionId: Long): Triple<Long, Long, Long> {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }

        val order = orderRepository.findById(orderId).get()
        val subOrder = order.subOrders.first()
        val subOrderId = requireNotNull(subOrder.id)
        val orderItemId = requireNotNull(subOrder.items.first().id)
        val productId = productRepository.findAll().first { it.options.any { o -> o.id == optionId } }.id!!

        mockMvc.post("/api/seller/orders/$subOrderId/ship") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ","trackingNumber":"1234567890"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/orders/sub-orders/$subOrderId/confirm-delivery") {
            with(user(buyer)); with(csrf())
        }.andExpect { status { isOk() } }

        return Triple(orderItemId, subOrderId, productId)
    }

    @Test
    fun `배송완료 항목에 포토리뷰를 작성하면 포인트가 적립되고 상품 평점이 갱신된다`() {
        val buyer = seedBuyer("review-buyer-1@example.com")
        val (seller, optionId) = seedSeller("REVIEW-SKU-1")
        val (orderItemId, _, productId) = deliverOneItem(buyer, seller, optionId)
        // 결제 확정 시 구매금액의 일부가 이미 적립되므로, 리뷰 적립분만 분리해 검증한다.
        val balanceBeforeReview =
            pointAccountRepository.findWithLotsByUserId(buyer.userId).map { it.balance }.orElse(0L)

        mockMvc.post("/api/reviews") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"orderItemId":$orderItemId,"rating":5,"content":"정말 만족스러운 상품이었습니다","imageUrls":["https://img/1.jpg"]}
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.rating") { value(5) }
            jsonPath("$.data.imageUrls.length()") { value(1) }
        }

        em.flush(); em.clear()
        val product = productRepository.findById(productId).get()
        assertEquals(1, product.reviewCount)
        assertEquals(0, product.avgRating.compareTo(java.math.BigDecimal("5.0")))

        // 포토 리뷰 적립(기본 정책 300P) 확인 — 결제 적립분과 분리한 순수 리뷰 적립분
        val account = pointAccountRepository.findWithLotsByUserId(buyer.userId).get()
        assertEquals(300L, account.balance - balanceBeforeReview)
    }

    @Test
    fun `이미 리뷰를 작성한 주문 항목에는 다시 작성할 수 없다`() {
        val buyer = seedBuyer("review-buyer-2@example.com")
        val (seller, optionId) = seedSeller("REVIEW-SKU-2")
        val (orderItemId, _, _) = deliverOneItem(buyer, seller, optionId)

        val body = """{"orderItemId":$orderItemId,"rating":4,"content":"괜찮은 상품이었어요 재구매의사있음"}"""
        mockMvc.post("/api/reviews") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/reviews") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("REVIEW-002") }
        }
    }

    @Test
    fun `타인의 주문 항목에는 리뷰를 작성할 수 없고 404로 존재를 숨긴다`() {
        val buyer = seedBuyer("review-buyer-3@example.com")
        val stranger = seedBuyer("review-stranger-3@example.com")
        val (seller, optionId) = seedSeller("REVIEW-SKU-3")
        val (orderItemId, _, _) = deliverOneItem(buyer, seller, optionId)

        mockMvc.post("/api/reviews") {
            with(user(stranger)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderItemId":$orderItemId,"rating":3,"content":"본인 리뷰 아님 테스트용 문구"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-009") }
        }
    }

    @Test
    fun `배송 전(SHIPPED) 상태의 항목에는 리뷰를 작성할 수 없다`() {
        val buyer = seedBuyer("review-buyer-4@example.com")
        val (seller, optionId) = seedSeller("REVIEW-SKU-4")

        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        val order = orderRepository.findById(orderId).get()
        val subOrderId = requireNotNull(order.subOrders.first().id)
        val orderItemId = requireNotNull(order.subOrders.first().items.first().id)

        mockMvc.post("/api/seller/orders/$subOrderId/ship") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ","trackingNumber":"1234567890"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/reviews") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderItemId":$orderItemId,"rating":5,"content":"아직 배송 중인데 작성 시도"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("REVIEW-003") }
        }
    }

    @Test
    fun `관리자가 리뷰를 숨김 처리하면 상품 목록과 평점 집계에서 제외된다`() {
        val buyer = seedBuyer("review-buyer-5@example.com")
        val (seller, optionId) = seedSeller("REVIEW-SKU-5")
        val (orderItemId, _, productId) = deliverOneItem(buyer, seller, optionId)

        val res = mockMvc.post("/api/reviews") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderItemId":$orderItemId,"rating":2,"content":"별로였어요 다시는 안살듯합니다"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val reviewId = Regex(""""id":(\d+)""").find(res)!!.groupValues[1].toLong()

        mockMvc.patch("/api/admin/reviews/$reviewId/status") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"status":"HIDDEN"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("HIDDEN") }
        }

        em.flush(); em.clear()
        val product = productRepository.findById(productId).get()
        assertEquals(0, product.reviewCount)

        mockMvc.get("/api/products/$productId/reviews") {
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(0) }
        }

        // 작성자 본인은 "내 리뷰" 목록에서 여전히 확인 가능(숨김 안내용)
        mockMvc.get("/api/me/reviews") {
            with(user(buyer))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(1) }
        }
    }

    @Test
    fun `동일 리뷰에 대한 중복 신고는 1회로 집계되고 임계치 도달 시 REPORTED로 전이한다`() {
        val buyer = seedBuyer("review-buyer-6@example.com")
        val (seller, optionId) = seedSeller("REVIEW-SKU-6")
        val (orderItemId, _, _) = deliverOneItem(buyer, seller, optionId)

        val res = mockMvc.post("/api/reviews") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderItemId":$orderItemId,"rating":1,"content":"허위 신고 테스트용 리뷰입니다"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val reviewId = Regex(""""id":(\d+)""").find(res)!!.groupValues[1].toLong()

        // 기본 정책 report_threshold = 5. 서로 다른 신고자 5명 + 동일 신고자 중복 신고 1명(무시되어야 함)
        (1..5).forEach { i ->
            val reporter = seedBuyer("reporter-$i-review6@example.com")
            mockMvc.post("/api/reviews/$reviewId/reports") {
                with(user(reporter)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"부적절한 리뷰 신고 사유 $i"}"""
            }.andExpect { status { isOk() } }
        }
        val duplicateReporter = seedBuyer("dup-reporter-review6@example.com")
        mockMvc.post("/api/reviews/$reviewId/reports") {
            with(user(duplicateReporter)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"중복 신고 시도"}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/reviews/$reviewId/reports") {
            with(user(duplicateReporter)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"reason":"중복 신고 재시도"}"""
        }.andExpect { status { isOk() } }

        // REPORTED 로 전이했지만 여전히 공개 목록에는 노출된다(AC12 — 자동 숨김 아님)
        mockMvc.get("/api/admin/reviews?reported=true") {
            with(user("admin").roles("ADMIN"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content[0].status") { value("REPORTED") }
        }
    }
}
