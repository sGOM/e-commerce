package com.example.starter.domain.seller

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
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
class SellerShipmentIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var subOrderRepository: SubOrderRepository
    @Autowired lateinit var roleRepository: RoleRepository

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    /** 판매자(ROLE_SELLER) + 상품을 만들고 (sellerUserDetails, optionId) 를 돌려준다. */
    private fun seedSeller(sku: String, stock: Int = 10): Pair<CustomUserDetails, Long> {
        val u = User(email = "$sku@seller.com", password = "{noop}x", name = sku)
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = stock, reserved = 0))
        product.addOption(option)
        val optionId = productRepository.save(product).options.first().id!!
        return CustomUserDetails(u) to optionId
    }

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** 구매자가 주문·결제까지 마친 뒤 해당 SubOrder id 를 돌려준다(PAID 상태). */
    private fun placePaidOrder(buyer: CustomUserDetails, optionId: Long): Long {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        return orderRepository.findById(orderId).get().subOrders.first().id!!
    }

    @Test
    fun `판매자가 본인 판매분에 송장을 등록하면 SHIPPED로 전이된다`() {
        val (seller, optionId) = seedSeller("SHIP-SKU-1")
        val buyer = seedBuyer("ship-buyer-1@example.com")
        val subOrderId = placePaidOrder(buyer, optionId)

        mockMvc.post("/api/seller/orders/$subOrderId/ship") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ대한통운","trackingNumber":"123456789"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("SHIPPED") }
            jsonPath("$.data.shipment.trackingNumber") { value("123456789") }
        }

        // 목록 조회(SHIPPED 필터)
        mockMvc.get("/api/seller/orders?status=SHIPPED") {
            with(user(seller))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
        }
    }

    @Test
    fun `남의 판매분은 발송할 수 없다`() {
        val (sellerA, optionId) = seedSeller("SHIP-SKU-2")
        val (sellerB, _) = seedSeller("SHIP-SKU-2-OTHER")
        val buyer = seedBuyer("ship-buyer-2@example.com")
        val subOrderId = placePaidOrder(buyer, optionId) // sellerA 판매분

        mockMvc.post("/api/seller/orders/$subOrderId/ship") {
            with(user(sellerB)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ","trackingNumber":"999"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-005") }
        }
    }

    @Test
    fun `결제 전(CREATED) 주문은 발송할 수 없다`() {
        val (seller, optionId) = seedSeller("SHIP-SKU-3")
        val buyer = seedBuyer("ship-buyer-3@example.com")
        // 결제 없이 주문만 생성
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        val subOrderId = orderRepository.findById(orderId).get().subOrders.first().id!!

        mockMvc.post("/api/seller/orders/$subOrderId/ship") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ","trackingNumber":"111"}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ORDER-006") }
        }
    }

    @Test
    fun `구매자는 발송된 하위 주문의 송장과 배송 조회 결과를 보고, 남의 주문·미발송 주문은 404 다`() {
        val (seller, optionId) = seedSeller("TRACK-SKU-1")
        val buyer = seedBuyer("track-buyer-1@example.com")
        val shipped = placePaidOrder(buyer, optionId)
        val notShipped = placePaidOrder(buyer, optionId)
        mockMvc.post("/api/seller/orders/$shipped/ship") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ대한통운","trackingNumber":"555"}"""
        }.andExpect { status { isOk() } }
        val orderId = subOrderRepository.findById(shipped).get().order.id!!

        mockMvc.get("/api/orders/$orderId") { with(user(buyer)) }.andExpect {
            jsonPath("$.data.subOrders[0].courier") { value("CJ대한통운") }
            jsonPath("$.data.subOrders[0].trackingNumber") { value("555") }
        }
        mockMvc.get("/api/orders/sub-orders/$shipped/tracking") { with(user(buyer)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.trackingNumber") { value("555") }
            jsonPath("$.data.supported") { value(true) }
            jsonPath("$.data.events[0].description") { value("송장이 등록되었습니다") } // 기본 Mock 조회기
        }
        mockMvc.get("/api/orders/sub-orders/$shipped/tracking") { with(user(seedBuyer("track-stranger@example.com"))) }
            .andExpect { status { isNotFound() } }
        mockMvc.get("/api/orders/sub-orders/$notShipped/tracking") { with(user(buyer)) }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ORDER-011") }
        }
    }
}
