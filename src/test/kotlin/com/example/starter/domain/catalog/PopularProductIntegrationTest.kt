package com.example.starter.domain.catalog

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
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
class PopularProductIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** ON_SALE 상품(옵션 1개, 재고 100) 생성 후 (productId, optionId) 반환. */
    private fun seedProduct(seller: Seller, name: String, sku: String): Pair<Long, Long> {
        val product = Product(seller = seller, name = name, basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        val saved = productRepository.save(product)
        return saved.id!! to saved.options.first().id!!
    }

    private fun placePaidOrder(buyer: CustomUserDetails, optionId: Long, quantity: Int) {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":$quantity}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `인기 상품은 누적 판매 수량 순으로 정렬된다`() {
        val seller = sellerRepository.save(
            Seller(
                userId = userRepository.save(User(email = "pop@seller.com", password = "{noop}x", name = "pop")).id!!,
                storeName = "인기상점", status = SellerStatus.ACTIVE,
            ),
        )
        val (lessId, lessOpt) = seedProduct(seller, "덜팔린상품", "POP-LESS")
        val (moreId, moreOpt) = seedProduct(seller, "많이팔린상품", "POP-MORE")

        // 많이팔린상품 3개, 덜팔린상품 1개 결제
        placePaidOrder(seedBuyer("pop-buyer-1@e.com"), moreOpt, 3)
        placePaidOrder(seedBuyer("pop-buyer-2@e.com"), lessOpt, 1)

        mockMvc.get("/api/products/popular").andExpect {
            status { isOk() }
            jsonPath("$.data[0].id") { value(moreId.toInt()) }
            jsonPath("$.data[0].soldQuantity") { value(3) }
            jsonPath("$.data[1].id") { value(lessId.toInt()) }
            jsonPath("$.data[1].soldQuantity") { value(1) }
        }
    }

    @Test
    fun `판매 이력이 없으면 인기 상품은 빈 목록이다`() {
        mockMvc.get("/api/products/popular?limit=5").andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }
}
