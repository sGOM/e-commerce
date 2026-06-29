package com.example.starter.domain.cart

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
class CartIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String, status: ProductStatus, stock: Int, additionalPrice: Long = 0): Long {
        val user = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = user.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = status)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = additionalPrice)
        option.assignInventory(Inventory(quantity = stock, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    @Test
    fun `장바구니에 담고 합계를 확인한다`() {
        val buyer = seedMember("cart-buyer-1@example.com")
        val optionId = seedOption("CART-SKU-1", ProductStatus.ON_SALE, stock = 10, additionalPrice = 2_000)

        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":3}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalQuantity") { value(3) }
            jsonPath("$.data.totalPrice") { value(36_000) } // (10000+2000)*3
            jsonPath("$.data.items[0].purchasable") { value(true) }
        }
    }

    @Test
    fun `재고보다 많이 담으면 409`() {
        val buyer = seedMember("cart-buyer-2@example.com")
        val optionId = seedOption("CART-SKU-2", ProductStatus.ON_SALE, stock = 2)

        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":5}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CATALOG-005") }
        }
    }

    @Test
    fun `같은 옵션을 두 번 담으면 수량이 합산된다`() {
        val buyer = seedMember("cart-buyer-3@example.com")
        val optionId = seedOption("CART-SKU-3", ProductStatus.ON_SALE, stock = 10)

        val body = """{"optionId":$optionId,"quantity":2}"""
        repeat(2) {
            mockMvc.post("/api/cart/items") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }
        }

        mockMvc.get("/api/cart") { with(user(buyer)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.items.length()") { value(1) }
            jsonPath("$.data.totalQuantity") { value(4) }
        }
    }

    @Test
    fun `판매중이 아닌 상품은 담을 수 없다`() {
        val buyer = seedMember("cart-buyer-4@example.com")
        val optionId = seedOption("CART-SKU-4", ProductStatus.SOLD_OUT, stock = 10)

        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CATALOG-004") }
        }
    }

    @Test
    fun `미인증 사용자는 장바구니에 접근할 수 없다`() {
        mockMvc.get("/api/cart").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("AUTH-001") }
        }
    }

    @Test
    fun `게스트 장바구니는 인증 없이 현재가와 합계를 계산한다`() {
        val optionId = seedOption("GUEST-SKU-1", ProductStatus.ON_SALE, stock = 10, additionalPrice = 2_000)

        // 같은 옵션을 두 줄로 보내도 수량이 합산된다
        mockMvc.post("/api/cart/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"items":[{"optionId":$optionId,"quantity":2},{"optionId":$optionId,"quantity":1}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.items.length()") { value(1) }
            jsonPath("$.data.items[0].itemId") { value(null) }
            jsonPath("$.data.totalQuantity") { value(3) }
            jsonPath("$.data.totalPrice") { value(36_000) } // (10000+2000)*3
            jsonPath("$.data.items[0].purchasable") { value(true) }
        }
    }

    @Test
    fun `게스트 장바구니는 재고 부족 항목을 구매불가로 표시한다`() {
        val optionId = seedOption("GUEST-SKU-2", ProductStatus.ON_SALE, stock = 1)

        mockMvc.post("/api/cart/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"items":[{"optionId":$optionId,"quantity":5}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.items[0].purchasable") { value(false) }
            jsonPath("$.data.items[0].availableStock") { value(1) }
        }
    }
}
