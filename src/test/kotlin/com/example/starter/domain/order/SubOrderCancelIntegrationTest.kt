package com.example.starter.domain.order

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
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
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class SubOrderCancelIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var em: EntityManager

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String, basePrice: Long = 10_000, stock: Int = 10): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = stock, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private fun addToCart(buyer: CustomUserDetails, optionId: Long, quantity: Int) {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":$quantity}"""
        }.andExpect { status { isOk() } }
    }

    private fun reservedOf(optionId: Long): Int {
        em.flush(); em.clear()
        return productRepository.findWithDetailById(
            productRepository.findAll().first { p -> p.options.any { it.id == optionId } }.id!!,
        ).get().options.first { it.id == optionId }.inventory!!.reserved
    }

    @Test
    fun `한 판매자분만 부분 취소하면 그 재고만 복원되고 주문은 PAID로 유지된다`() {
        val buyer = seedBuyer("sub-cancel-1@example.com")
        val optionA = seedOption("SUBC-A", basePrice = 20_000) // 셀러 A
        val optionB = seedOption("SUBC-B", basePrice = 30_000) // 셀러 B
        addToCart(buyer, optionA, 1)
        addToCart(buyer, optionB, 1)

        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }

        val subOrderA = orderRepository.findById(orderId).get().subOrders.first { it.subtotal == 20_000L }

        // A 판매자분만 부분 취소
        mockMvc.post("/api/orders/sub-orders/${subOrderA.id}/cancel") {
            with(user(buyer)); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PAID") } // 주문 전체는 아직 PAID
        }

        // A 재고만 복원(0), B 재고 유지(1)
        assertEquals(0, reservedOf(optionA))
        assertEquals(1, reservedOf(optionB))
        // 결제는 아직 PAID(부분 환불 기록만)
        assertEquals(PaymentStatus.PAID, paymentRepository.findByOrderId(orderId).get().status)
    }

    @Test
    fun `모든 하위 주문을 취소하면 주문 전체가 CANCELED되고 결제가 취소된다`() {
        val buyer = seedBuyer("sub-cancel-2@example.com")
        val optionA = seedOption("SUBC2-A", basePrice = 20_000)
        val optionB = seedOption("SUBC2-B", basePrice = 30_000)
        addToCart(buyer, optionA, 1)
        addToCart(buyer, optionB, 1)

        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }

        val order = orderRepository.findById(orderId).get()
        val subIds = order.subOrders.map { it.id!! }
        // payableShare 합 = payableAmount
        assertEquals(order.payableAmount, order.subOrders.sumOf { it.payableShare })

        subIds.dropLast(1).forEach { sid ->
            mockMvc.post("/api/orders/sub-orders/$sid/cancel") { with(user(buyer)); with(csrf()) }
                .andExpect { status { isOk() } }
        }
        // 마지막 하위 주문 취소 → 전체 CANCELED
        mockMvc.post("/api/orders/sub-orders/${subIds.last()}/cancel") {
            with(user(buyer)); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CANCELED") }
        }
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(orderId).get().status)
    }

    @Test
    fun `이미 취소된 하위 주문은 다시 취소할 수 없다`() {
        val buyer = seedBuyer("sub-cancel-3@example.com")
        val optionId = seedOption("SUBC3", basePrice = 10_000)
        addToCart(buyer, optionId, 1)
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        val subId = orderRepository.findById(orderId).get().subOrders.first().id!!

        mockMvc.post("/api/orders/sub-orders/$subId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }
        mockMvc.post("/api/orders/sub-orders/$subId/cancel") { with(user(buyer)); with(csrf()) }
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("ORDER-003") }
            }
    }
}
