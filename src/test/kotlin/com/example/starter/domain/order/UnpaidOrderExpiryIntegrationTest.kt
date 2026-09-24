package com.example.starter.domain.order

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.repository.OrderRepository
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
import org.springframework.test.web.servlet.post
import java.time.Duration
import java.time.Instant

/**
 * 미결제 주문 자동 만료(ROADMAP 1.6). 건별 REQUIRES_NEW 라 테스트 트랜잭션을 쓰지 않는다 —
 * 남는 데이터는 CREATED/CANCELED 주문뿐이고, orders 를 참조하는 gift_claims 는 직접 정리한다.
 */
@AutoConfigureMockMvc
class UnpaidOrderExpiryIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var giftClaimRepository: GiftClaimRepository
    @Autowired lateinit var unpaidOrderExpiryService: UnpaidOrderExpiryService
    @Autowired lateinit var orderService: OrderService

    private lateinit var buyer: CustomUserDetails

    private fun createOrder(email: String, gift: Boolean = false): Long {
        buyer = CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))
        val u = userRepository.save(User(email = "s-$email", password = "{noop}x", name = email))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = email, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = "EXP-$email", additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        val optionId = productRepository.save(product).options.first().id!!
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val address = if (gift) """"isGift":true""" else """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
    }

    private fun statusOf(orderId: Long) = orderRepository.findById(orderId).get().status

    @Test
    fun `결제 기한이 지난 미결제 주문만 취소하고 선물 링크도 마감한다`() {
        val plain = createOrder("exp-1@example.com")
        val gift = createOrder("exp-2@example.com", gift = true)

        // 기한(기본 30분) 전에는 그대로
        unpaidOrderExpiryService.expireUnpaid(Instant.now())
        assertEquals(OrderStatus.CREATED, statusOf(plain))

        // 공유 DB 에 다른 테스트의 CREATED 주문이 있을 수 있어 건수 대신 상태로 확인한다
        unpaidOrderExpiryService.expireUnpaid(Instant.now().plus(Duration.ofHours(1)))

        assertEquals(OrderStatus.CANCELED, statusOf(plain))
        assertEquals(OrderStatus.CANCELED, statusOf(gift))
        val claim = giftClaimRepository.findByOrderId(gift).get()
        assertEquals(GiftClaimStatus.CANCELED, claim.status)

        giftClaimRepository.delete(claim) // gift_claims → orders FK 가 orders 전체 삭제 테스트를 막지 않게
    }

    @Test
    fun `목록 조회 뒤 결제된 주문은 잠금 후 상태를 다시 보고 건드리지 않는다`() {
        val orderId = createOrder("exp-3@example.com")
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }

        val expired = orderService.expireUnpaidOrder(orderId, Instant.now().plus(Duration.ofHours(1)))

        assertEquals(false, expired)
        assertEquals(OrderStatus.PAID, statusOf(orderId))
        // 정리: 커밋된 PAID 주문이 정산 집계 테스트에 섞이지 않게 취소
        mockMvc.post("/api/orders/$orderId/cancel") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
    }
}
