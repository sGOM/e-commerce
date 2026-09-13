package com.example.starter.domain.admin

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class AdminOperationIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var issuedCouponRepository: IssuedCouponRepository
    @Autowired lateinit var paymentRepository: PaymentRepository

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    @Test
    fun `관리자가 쿠폰을 발행하고 회원에게 발급한다`() {
        val member = seedMember("coupon-target@example.com")

        mockMvc.post("/api/admin/coupons") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"name":"신규가입 10%","discountType":"RATE","discountValue":10,"minOrderAmount":0,
                 "validFrom":"2020-01-01T00:00:00Z","validUntil":"2030-01-01T00:00:00Z",
                 "issueToUserIds":[${member.userId}]}
            """.trimIndent()
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.issuedCount") { value(1) }
        }

        // 회원에게 실제 발급되었는지
        assertEquals(1, issuedCouponRepository.findByUserIdOrderByIdDesc(member.userId).size)
    }

    @Test
    fun `관리자가 전체 주문을 검색하고 환불한다`() {
        val buyer = seedMember("admin-refund-buyer@example.com")
        val optionId = seedOption("ADMIN-SKU-1")

        // 주문 + 결제
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

        // 전체 주문 검색(PAID)
        mockMvc.get("/api/admin/orders?status=PAID") {
            with(user("admin").roles("ADMIN"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)) }
        }

        // 관리자 환불
        mockMvc.post("/api/admin/orders/$orderId/refund") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CANCELED") }
        }
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(orderId).get().status)
    }

    @Test
    fun `일반 회원은 관리자 API에 접근할 수 없다`() {
        val member = seedMember("not-admin@example.com")
        mockMvc.get("/api/admin/orders") {
            with(user(member))
        }.andExpect {
            status { isForbidden() }
        }
    }
}
