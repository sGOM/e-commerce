package com.example.starter.domain.order

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.membership.MembershipBenefitService
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import com.ninjasquad.springmockk.SpykBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/** 기본 배송비(ROADMAP 7.1): 판매자(SubOrder) 단위 부과, 멤버십 무료배송 면제, 관리자 정책 변경. */
@AutoConfigureMockMvc
@Transactional
class ShippingFeeIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository

    @SpykBean lateinit var membershipBenefitService: MembershipBenefitService

    private fun seedBuyer(email: String) = CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String, price: Long): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = price, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private fun order(buyer: CustomUserDetails, vararg optionIds: Long): ResultActionsDsl {
        optionIds.forEach { optionId ->
            mockMvc.post("/api/cart/items") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionId":$optionId,"quantity":1}"""
            }.andExpect { status { isOk() } }
        }
        return mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}}"""
        }
    }

    @Test
    fun `판매자마다 기본 배송비 3000원이 붙어 결제금액에 더해진다`() {
        val buyer = seedBuyer("ship-1@example.com")

        order(buyer, seedOption("SHIP1-A", 10_000), seedOption("SHIP1-B", 20_000)).andExpect {
            status { isOk() }
            jsonPath("$.data.totalAmount") { value(30_000) }
            jsonPath("$.data.deliveryFeeTotal") { value(6_000) }
            jsonPath("$.data.payableAmount") { value(36_000) }
            jsonPath("$.data.subOrders[0].deliveryFee") { value(3_000) }
        }
    }

    @Test
    fun `멤버십 무료배송 회원은 기본 배송비가 면제된다`() {
        val buyer = seedBuyer("ship-2@example.com")
        every { membershipBenefitService.isFreeShippingActive(buyer.userId, any()) } returns true

        order(buyer, seedOption("SHIP2-A", 10_000)).andExpect {
            jsonPath("$.data.deliveryFeeTotal") { value(0) }
            jsonPath("$.data.payableAmount") { value(10_000) }
        }
    }

    @Test
    fun `관리자가 바꾼 배송비가 공개 조회와 새 주문에 반영된다`() {
        mockMvc.patch("/api/admin/shipping-policy") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"baseFee":2500}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/shipping-policy").andExpect {
            status { isOk() }
            jsonPath("$.data.baseFee") { value(2_500) }
        }
        order(seedBuyer("ship-3@example.com"), seedOption("SHIP3-A", 10_000)).andExpect {
            jsonPath("$.data.deliveryFeeTotal") { value(2_500) }
        }
    }

    @Test
    fun `배송비는 음수로 바꿀 수 없다`() {
        mockMvc.patch("/api/admin/shipping-policy") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"baseFee":-1}"""
        }.andExpect { status { isBadRequest() } }
    }
}
