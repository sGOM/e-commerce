package com.example.starter.domain.payment

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.point.repository.PointAccountRepository
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
class PaymentIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var pointAccountRepository: PointAccountRepository

    private val ordererBody = """"ordererName":"홍길동","ordererPhone":"010-1234-5678","ordererEmail":"buyer@example.com","shippingAddress":{"receiverName":"홍길동","receiverPhone":"010-1234-5678","zipcode":"12345","address1":"서울시 강남구 1"}"""

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

    /** 장바구니 담기 → 주문 생성, 생성된 orderId 반환 */
    private fun placeOrder(buyer: CustomUserDetails, optionId: Long, quantity: Int): Long {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":$quantity}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
    }

    @Test
    fun `결제가 완료되면 주문과 하위주문이 PAID로 전이된다`() {
        val buyer = seedMember("pay-buyer-1@example.com")
        val optionId = seedOption("PAY-SKU-1")
        val orderId = placeOrder(buyer, optionId, 2)

        mockMvc.post("/api/payments/$orderId") {
            with(user(buyer)); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PAID") }
            jsonPath("$.data.amount") { value(20_000) }
            jsonPath("$.data.transactionId") { exists() }
        }

        // 주문 상세에서 상태 전이 확인
        mockMvc.get("/api/orders/$orderId") { with(user(buyer)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PAID") }
            jsonPath("$.data.subOrders[0].status") { value("PAID") }
        }
    }

    @Test
    fun `같은 주문을 두 번 결제해도 결제는 1건만 생성된다(멱등)`() {
        val buyer = seedMember("pay-buyer-2@example.com")
        val optionId = seedOption("PAY-SKU-2")
        val orderId = placeOrder(buyer, optionId, 1)

        val first = mockMvc.post("/api/payments/$orderId") {
            with(user(buyer)); with(csrf())
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val firstTx = Regex(""""transactionId":"([^"]+)"""").find(first)!!.groupValues[1]

        val second = mockMvc.post("/api/payments/$orderId") {
            with(user(buyer)); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PAID") }
        }.andReturn().response.contentAsString
        val secondTx = Regex(""""transactionId":"([^"]+)"""").find(second)!!.groupValues[1]

        assertEquals(firstTx, secondTx, "재승인 없이 동일 거래번호 반환")
        assertEquals(1, paymentRepository.findAll().count { it.orderId == orderId }, "결제는 1건만")
    }

    @Test
    fun `결제 후 주문을 취소하면 환불되고 적립 포인트가 회수된다`() {
        val buyer = seedMember("pay-refund@example.com")
        val optionId = seedOption("PAY-SKU-REFUND")
        val orderId = placeOrder(buyer, optionId, 1) // 결제금액 10,000

        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }
        // 기본 적립률 1% → 100 적립
        assertEquals(100, pointAccountRepository.findByUserId(buyer.userId).get().balance)

        // 결제 후 취소(배송 전) → 환불
        mockMvc.post("/api/orders/$orderId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect {
                status { isOk() }
                jsonPath("$.data.status") { value("CANCELED") }
            }

        // 결제는 CANCELED, 적립 포인트 회수되어 0
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(orderId).get().status)
        assertEquals(0, pointAccountRepository.findByUserId(buyer.userId).get().balance)
    }

    @Test
    fun `남의 주문은 결제할 수 없다`() {
        val owner = seedMember("pay-owner@example.com")
        val stranger = seedMember("pay-stranger@example.com")
        val optionId = seedOption("PAY-SKU-3")
        val orderId = placeOrder(owner, optionId, 1)

        mockMvc.post("/api/payments/$orderId") {
            with(user(stranger)); with(csrf())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-001") }
        }
    }
}
