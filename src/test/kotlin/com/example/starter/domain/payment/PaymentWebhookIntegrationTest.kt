package com.example.starter.domain.payment

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.gateway.PaymentApproveResult
import com.example.starter.domain.payment.gateway.PaymentGateway
import com.example.starter.domain.payment.gateway.PaymentLookupResult
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 토스 결제 웹훅(ROADMAP 1.4). PAYMENT_STATUS_CHANGED 에는 서명이 없으므로 payload 는 paymentKey 만 쓰고
 * 상태는 PG 재조회([PaymentGateway.lookup]) 결과만 믿는다. 웹훅은 CSRF 토큰·세션 없이 들어온다.
 */
@AutoConfigureMockMvc
@Transactional
class PaymentWebhookIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var paymentRepository: PaymentRepository

    @MockkBean lateinit var paymentGateway: PaymentGateway

    @BeforeEach
    fun stubGateway() {
        every { paymentGateway.approve(any()) } answers { PaymentApproveResult(true, "pk-" + UUID.randomUUID(), "승인") }
    }

    /** 결제 완료 주문을 만들고 (orderNumber, paymentKey) 반환 */
    private fun paidOrder(email: String): Pair<String, String> {
        val buyer = CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))
        val u = userRepository.save(User(email = "s-$email", password = "{noop}x", name = email))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = email, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = "WH-$email", additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        val optionId = productRepository.save(product).options.first().id!!

        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        val order = orderRepository.findById(orderId).get()
        return order.orderNumber to paymentRepository.findByOrderId(orderId).get().pgTransactionId!!
    }

    private fun sendWebhook(paymentKey: String, eventType: String = "PAYMENT_STATUS_CHANGED") =
        mockMvc.post("/api/payments/webhook/toss") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"eventType":"$eventType","createdAt":"2026-09-24T10:00:00.000000","data":{"paymentKey":"$paymentKey","status":"CANCELED"}}"""
        }

    private fun orderStatus(orderNumber: String) = orderRepository.findByOrderNumber(orderNumber).get().status.name

    @Test
    fun `PG 에서 취소된 결제는 PG 재호출 없이 주문·결제를 취소로 맞춘다`() {
        val (orderNumber, paymentKey) = paidOrder("wh-1@example.com")
        every { paymentGateway.lookup(paymentKey) } returns PaymentLookupResult(paymentKey, orderNumber, "CANCELED")

        sendWebhook(paymentKey).andExpect { status { isOk() } }

        assertEquals("CANCELED", orderStatus(orderNumber))
        val orderId = orderRepository.findByOrderNumber(orderNumber).get().id!!
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(orderId).get().status)
        verify(exactly = 0) { paymentGateway.cancel(any()) }

        // 재전송(멱등): 이미 취소면 아무것도 하지 않고 200
        sendWebhook(paymentKey).andExpect { status { isOk() } }
    }

    @Test
    fun `payload 가 취소라고 해도 PG 재조회 결과가 취소가 아니면 무시한다`() {
        val (orderNumber, paymentKey) = paidOrder("wh-2@example.com")
        every { paymentGateway.lookup(paymentKey) } returns PaymentLookupResult(paymentKey, orderNumber, "DONE")

        sendWebhook(paymentKey).andExpect { status { isOk() } }

        assertEquals("PAID", orderStatus(orderNumber))
    }

    @Test
    fun `재조회한 주문번호가 우리 결제와 다르면 무시한다`() {
        val (orderNumber, paymentKey) = paidOrder("wh-3@example.com")
        every { paymentGateway.lookup(paymentKey) } returns PaymentLookupResult(paymentKey, "ORD-OTHER", "CANCELED")

        sendWebhook(paymentKey).andExpect { status { isOk() } }

        assertEquals("PAID", orderStatus(orderNumber))
    }

    @Test
    fun `다른 이벤트 타입은 조회 없이 무시한다`() {
        sendWebhook("pk-x", eventType = "DEPOSIT_CALLBACK").andExpect { status { isOk() } }
        verify(exactly = 0) { paymentGateway.lookup(any()) }
    }
}
