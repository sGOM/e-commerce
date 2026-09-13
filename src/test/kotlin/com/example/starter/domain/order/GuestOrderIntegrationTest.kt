package com.example.starter.domain.order

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
class GuestOrderIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository

    private val address = """"shippingAddress":{"receiverName":"수령인","receiverPhone":"010-9999-0000","zipcode":"54321","address1":"부산시 1"}"""

    private fun seedOption(sku: String, stock: Int): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = stock, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    @Test
    fun `비회원이 주문을 생성하고 주문번호와 연락처로 조회한다`() {
        val optionId = seedOption("GUEST-ORD-1", stock = 10)

        val res = mockMvc.post("/api/orders/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"비회원","ordererPhone":"010-1111-2222","ordererEmail":"guest@e.com",$address,"items":[{"optionId":$optionId,"quantity":2}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.payableAmount") { value(20_000) }
            jsonPath("$.data.orderNumber") { exists() }
            jsonPath("$.data.shippingAddress.receiverName") { value("수령인") }
        }.andReturn().response.contentAsString
        val orderNumber = Regex(""""orderNumber":"([^"]+)"""").find(res)!!.groupValues[1]

        // 주문번호 + 연락처 일치 → 조회 성공
        mockMvc.post("/api/orders/guest/lookup") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderNumber":"$orderNumber","ordererPhone":"010-1111-2222"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.orderNumber") { value(orderNumber) }
        }
    }

    @Test
    fun `연락처가 다르면 게스트 주문을 조회할 수 없다`() {
        val optionId = seedOption("GUEST-ORD-2", stock = 10)
        val res = mockMvc.post("/api/orders/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"비회원","ordererPhone":"010-1111-2222","ordererEmail":"guest@e.com",$address,"items":[{"optionId":$optionId,"quantity":1}]}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderNumber = Regex(""""orderNumber":"([^"]+)"""").find(res)!!.groupValues[1]

        mockMvc.post("/api/orders/guest/lookup") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderNumber":"$orderNumber","ordererPhone":"010-0000-0000"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-001") }
        }
    }

    @Test
    fun `재고가 부족하면 게스트 주문도 409로 거절된다`() {
        val optionId = seedOption("GUEST-ORD-3", stock = 1)

        mockMvc.post("/api/orders/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"비회원","ordererPhone":"010-1","ordererEmail":"guest@e.com",$address,"items":[{"optionId":$optionId,"quantity":5}]}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CATALOG-005") }
        }
    }

    private fun placeGuestOrder(optionId: Long, phone: String): String {
        val res = mockMvc.post("/api/orders/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"비회원","ordererPhone":"$phone","ordererEmail":"guest@e.com",$address,"items":[{"optionId":$optionId,"quantity":1}]}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return Regex(""""orderNumber":"([^"]+)"""").find(res)!!.groupValues[1]
    }

    @Test
    fun `회원이 게스트 주문을 본인 계정에 연결하면 내 주문 목록에 나타난다`() {
        val optionId = seedOption("CLAIM-1", stock = 10)
        val orderNumber = placeGuestOrder(optionId, "010-7777-8888")
        val member = CustomUserDetails(userRepository.save(User(email = "claimer@e.com", password = "{noop}x", name = "회원")))

        // 주문번호 + 연락처 일치 → 연결 성공
        mockMvc.post("/api/orders/claim") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderNumber":"$orderNumber","ordererPhone":"010-7777-8888"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.orderNumber") { value(orderNumber) }
        }

        // 연결 후에는 회원 주문 목록/상세에서 조회된다
        mockMvc.get("/api/orders") { with(user(member)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(1) }
        }
    }

    @Test
    fun `이미 연결된 주문은 다시 연결할 수 없다`() {
        val optionId = seedOption("CLAIM-2", stock = 10)
        val orderNumber = placeGuestOrder(optionId, "010-3333-4444")
        val first = CustomUserDetails(userRepository.save(User(email = "claim-first@e.com", password = "{noop}x", name = "회원1")))
        val second = CustomUserDetails(userRepository.save(User(email = "claim-second@e.com", password = "{noop}x", name = "회원2")))

        val body = """{"orderNumber":"$orderNumber","ordererPhone":"010-3333-4444"}"""
        mockMvc.post("/api/orders/claim") {
            with(user(first)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isOk() } }

        // 다른 회원이 같은 주문을 연결 시도 → 409
        mockMvc.post("/api/orders/claim") {
            with(user(second)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ORDER-007") }
        }
    }

    @Test
    fun `연락처가 다르면 게스트 주문을 연결할 수 없다`() {
        val optionId = seedOption("CLAIM-3", stock = 10)
        val orderNumber = placeGuestOrder(optionId, "010-5555-6666")
        val member = CustomUserDetails(userRepository.save(User(email = "claim-wrong@e.com", password = "{noop}x", name = "회원")))

        mockMvc.post("/api/orders/claim") {
            with(user(member)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"orderNumber":"$orderNumber","ordererPhone":"010-0000-0000"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-001") }
        }
    }

    @Test
    fun `배송지가 없으면 게스트 주문은 400`() {
        val optionId = seedOption("GUEST-ORD-4", stock = 10)

        mockMvc.post("/api/orders/guest") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"비회원","ordererPhone":"010-1","ordererEmail":"guest@e.com","items":[{"optionId":$optionId,"quantity":1}]}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
