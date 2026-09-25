package com.example.starter.domain.order

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.InventoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
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
@Transactional // 각 테스트 종료 후 롤백 → 외부 DB 재사용 시에도 격리 보장
class OrderIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var inventoryRepository: InventoryRepository
    @Autowired lateinit var em: EntityManager

    private val ordererBody = """"ordererName":"홍길동","ordererPhone":"010-1234-5678","ordererEmail":"buyer@example.com","shippingAddress":{"receiverName":"홍길동","receiverPhone":"010-1234-5678","zipcode":"12345","address1":"서울시 강남구 1"}"""

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** 새 판매자 상점에 옵션 1개짜리 상품을 만들고 옵션 id 를 돌려준다(sku 마다 별도 셀러). */
    private fun seedOption(sku: String, status: ProductStatus, stock: Int, additionalPrice: Long = 0): Long {
        val user = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = user.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = status)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = additionalPrice)
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

    /** 1차 캐시를 비워 재고(JPQL 벌크 UPDATE 결과)를 DB 에서 fresh 로 읽는다. */
    private fun reservedOf(optionId: Long): Int {
        em.flush(); em.clear()
        return inventoryRepository.findAll().first { it.option.id == optionId }.reserved
    }

    @Test
    fun `장바구니 전체를 주문으로 전환하고 재고를 예약한다`() {
        val buyer = seedMember("order-buyer-1@example.com")
        val optionId = seedOption("ORD-SKU-1", ProductStatus.ON_SALE, stock = 10, additionalPrice = 2_000)
        addToCart(buyer, optionId, 3)

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CREATED") }
            jsonPath("$.data.subOrders.length()") { value(1) }
            jsonPath("$.data.totalAmount") { value(36_000) } // (10000+2000)*3
            jsonPath("$.data.payableAmount") { value(39_000) } // + 배송비 3000
            jsonPath("$.data.orderNumber") { exists() }
        }

        // 재고 예약 확인 + 장바구니 비워짐
        org.junit.jupiter.api.Assertions.assertEquals(3, reservedOf(optionId))
        mockMvc.get("/api/cart") { with(user(buyer)) }.andExpect {
            jsonPath("$.data.items.length()") { value(0) }
        }
    }

    @Test
    fun `여러 판매자 상품은 SubOrder로 분리된다`() {
        val buyer = seedMember("order-buyer-2@example.com")
        val optionA = seedOption("ORD-SKU-2A", ProductStatus.ON_SALE, stock = 5)
        val optionB = seedOption("ORD-SKU-2B", ProductStatus.ON_SALE, stock = 5)
        addToCart(buyer, optionA, 1)
        addToCart(buyer, optionB, 2)

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.subOrders.length()") { value(2) }
        }
    }

    @Test
    fun `재고가 부족하면 409와 함께 주문이 롤백된다`() {
        val buyer = seedMember("order-buyer-3@example.com")
        val optionId = seedOption("ORD-SKU-3", ProductStatus.ON_SALE, stock = 1)
        addToCart(buyer, optionId, 1)
        // 담은 뒤 다른 경로로 재고가 모두 예약되어 가용 0이 된 상황을 만든다
        inventoryRepository.reserve(optionId, 1)

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CATALOG-005") }
        }

        // 주문 실패 → 추가 예약 없음(여전히 1만 예약), 장바구니 유지
        org.junit.jupiter.api.Assertions.assertEquals(1, reservedOf(optionId))
        mockMvc.get("/api/cart") { with(user(buyer)) }.andExpect {
            jsonPath("$.data.items.length()") { value(1) }
        }
    }

    @Test
    fun `빈 장바구니로는 주문할 수 없다`() {
        val buyer = seedMember("order-buyer-4@example.com")

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("ORDER-002") }
        }
    }

    @Test
    fun `주문을 취소하면 재고가 복원된다`() {
        val buyer = seedMember("order-buyer-5@example.com")
        val optionId = seedOption("ORD-SKU-5", ProductStatus.ON_SALE, stock = 10)
        addToCart(buyer, optionId, 4)

        val response = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(response)!!.groupValues[1].toLong()
        org.junit.jupiter.api.Assertions.assertEquals(4, reservedOf(optionId))

        mockMvc.post("/api/orders/$orderId/cancel") {
            with(user(buyer)); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("CANCELED") }
            jsonPath("$.data.subOrders[0].status") { value("CANCELED") }
        }

        org.junit.jupiter.api.Assertions.assertEquals(0, reservedOf(optionId))
    }

    @Test
    fun `남의 주문은 조회할 수 없다`() {
        val owner = seedMember("order-owner@example.com")
        val stranger = seedMember("order-stranger@example.com")
        val optionId = seedOption("ORD-SKU-6", ProductStatus.ON_SALE, stock = 10)
        addToCart(owner, optionId, 1)
        val response = mockMvc.post("/api/orders") {
            with(user(owner)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = "{$ordererBody}"
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(response)!!.groupValues[1].toLong()

        mockMvc.get("/api/orders/$orderId") { with(user(stranger)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-001") }
        }
    }
}
