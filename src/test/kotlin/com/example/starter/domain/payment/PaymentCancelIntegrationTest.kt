package com.example.starter.domain.payment

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.gift.GiftExpiryBatchService
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.gateway.PaymentApproveResult
import com.example.starter.domain.payment.gateway.PaymentCancelCommand
import com.example.starter.domain.payment.gateway.PaymentCancelResult
import com.example.starter.domain.payment.gateway.PaymentGateway
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 주문 취소가 PG 결제 취소로 이어지는지 검증한다(ROADMAP 1.3). PG 롤백 의미를 확인하려고 테스트 트랜잭션을
 * 쓰지 않는다 — 실패 시 서비스 트랜잭션이 실제로 롤백돼야 하므로 결과는 새 요청으로 다시 읽는다.
 */
@AutoConfigureMockMvc
class PaymentCancelIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var giftExpiryBatchService: GiftExpiryBatchService
    @Autowired lateinit var giftClaimRepository: GiftClaimRepository

    @MockkBean lateinit var paymentGateway: PaymentGateway

    private val body = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}}"""

    @BeforeEach
    fun stubGateway() {
        every { paymentGateway.approve(any()) } returns PaymentApproveResult(true, "TX-PG", "승인")
        every { paymentGateway.cancel(any()) } returns PaymentCancelResult(true, "취소")
    }

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String, basePrice: Long): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    /** 두 판매자(20,000 / 30,000) 상품으로 주문을 만들고, [pay] 면 결제까지 한 뒤 orderId 반환 */
    private fun placeTwoSellerOrder(buyer: CustomUserDetails, prefix: String, pay: Boolean = true): Long {
        listOf(seedOption("$prefix-A", 20_000), seedOption("$prefix-B", 30_000)).forEach { optionId ->
            mockMvc.post("/api/cart/items") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionId":$optionId,"quantity":1}"""
            }.andExpect { status { isOk() } }
        }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        if (pay) {
            mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        }
        return orderId
    }

    private fun subOrderIdWithSubtotal(buyer: CustomUserDetails, orderId: Long, subtotal: Long): Long {
        val json = mockMvc.get("/api/orders/$orderId") { with(user(buyer)) }.andReturn().response.contentAsString
        return Regex(""""subOrderId":(\d+),[^}]*?"subtotal":$subtotal""").find(json)!!.groupValues[1].toLong()
    }

    @Test
    fun `부분 취소는 해당 몫만, 이어진 전체 취소는 남은 금액만 PG 에 취소 요청한다`() {
        val buyer = seedBuyer("pg-cancel-1@example.com")
        val orderId = placeTwoSellerOrder(buyer, "PGC1")
        val subOrderA = subOrderIdWithSubtotal(buyer, orderId, 20_000)

        mockMvc.post("/api/orders/sub-orders/$subOrderA/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }
        verify(exactly = 1) {
            paymentGateway.cancel(PaymentCancelCommand("TX-PG", 23_000, "부분 취소", "cancel-sub-$subOrderA"))
        }

        mockMvc.post("/api/orders/$orderId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }
        verify(exactly = 1) {
            paymentGateway.cancel(PaymentCancelCommand("TX-PG", 33_000, "주문 취소", "cancel-order-$orderId"))
        }
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(orderId).get().status)
    }

    @Test
    fun `PG 취소가 실패하면 주문 취소 전체가 롤백된다`() {
        val buyer = seedBuyer("pg-cancel-2@example.com")
        val orderId = placeTwoSellerOrder(buyer, "PGC2")
        every { paymentGateway.cancel(any()) } returns PaymentCancelResult(false, "PG 오류")

        mockMvc.post("/api/orders/$orderId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect {
                status { isBadGateway() }
                jsonPath("$.code") { value("PAYMENT-002") }
            }

        mockMvc.get("/api/orders/$orderId") { with(user(buyer)) }.andExpect {
            jsonPath("$.data.status") { value("PAID") }
            jsonPath("$.data.subOrders[0].status") { value("PAID") }
        }
        assertEquals(PaymentStatus.PAID, paymentRepository.findByOrderId(orderId).get().status)

        // 롤백 후 재시도는 같은 멱등키로 성공한다. 테스트 트랜잭션이 없어 PAID 주문이 남으면 정산 집계 테스트에 섞이므로 정리도 겸한다.
        every { paymentGateway.cancel(any()) } returns PaymentCancelResult(true, "취소")
        mockMvc.post("/api/orders/$orderId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }
        verify(exactly = 2) { paymentGateway.cancel(match { it.idempotencyKey == "cancel-order-$orderId" }) }
    }

    @Test
    fun `결제 전 주문 취소는 PG 를 호출하지 않는다`() {
        val buyer = seedBuyer("pg-cancel-3@example.com")
        val orderId = placeTwoSellerOrder(buyer, "PGC3", pay = false)

        mockMvc.post("/api/orders/$orderId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }

        verify(exactly = 0) { paymentGateway.cancel(any()) }
    }

    @Test
    fun `선물 만료 배치에서 한 건의 PG 취소가 실패해도 다른 건의 취소는 커밋된다`() {
        val buyer = seedBuyer("pg-cancel-gift@example.com")
        val (failing, ok) = listOf("PGG-1", "PGG-2").map { sku ->
            val optionId = seedOption(sku, 10_000)
            mockMvc.post("/api/cart/items") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionId":$optionId,"quantity":1}"""
            }.andExpect { status { isOk() } }
            val res = mockMvc.post("/api/orders") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com","isGift":true}"""
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString
            val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
            mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
            orderId
        }
        every { paymentGateway.cancel(match { it.idempotencyKey == "cancel-order-$failing" }) } returns
            PaymentCancelResult(false, "PG 오류")

        val result = giftExpiryBatchService.expireDueClaims(Instant.now().plus(3650, ChronoUnit.DAYS))

        assertEquals(1, result.erroredCount)
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(ok).get().status)
        assertEquals(PaymentStatus.PAID, paymentRepository.findByOrderId(failing).get().status)

        // 정리(테스트 트랜잭션 없음): 남은 PAID 주문은 정산 집계 테스트에 섞이지 않도록 취소
        every { paymentGateway.cancel(any()) } returns PaymentCancelResult(true, "취소")
        mockMvc.post("/api/orders/$failing/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }
        // gift_claims → orders FK 가 orders 를 전부 지우는 테스트(OrderConcurrencyIntegrationTest) 정리를 막지 않게 한다
        listOf(failing, ok).forEach { id -> giftClaimRepository.findByOrderId(id).ifPresent(giftClaimRepository::delete) }
    }
}
