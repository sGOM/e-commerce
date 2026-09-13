package com.example.starter.domain.settlement

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
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
import java.time.LocalDate
import java.time.ZoneId

@AutoConfigureMockMvc
@Transactional
class SellerDashboardIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var roleRepository: RoleRepository

    private fun seedSeller(sku: String, basePrice: Long): Pair<CustomUserDetails, Long> {
        val u = User(email = "$sku@seller.com", password = "{noop}x", name = sku)
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = "store-$sku", status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        return CustomUserDetails(u) to productRepository.save(product).options.first().id!!
    }

    private fun placePaidOrder(optionId: Long, quantity: Int) {
        val buyer = CustomUserDetails(userRepository.save(User(email = "dash-buyer@example.com", password = "{noop}x", name = "구매")))
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":$quantity}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",""" +
                """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `판매자 대시보드는 기간 매출과 미정산·지급대기 금액을 보여준다`() {
        val (seller, optionId) = seedSeller("DASH-1", basePrice = 100_000)
        placePaidOrder(optionId, 2) // 매출 200,000
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

        mockMvc.get("/api/seller/dashboard?from=$today&to=$today") { with(user(seller)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.orderCount") { value(1) }
            jsonPath("$.data.salesAmount") { value(200_000) }
            jsonPath("$.data.unsettledAmount") { value(200_000) }
            jsonPath("$.data.pendingPayoutAmount") { value(0) }
        }
        // 기간 밖이면 매출에서 빠진다(미정산 금액은 기간과 무관)
        mockMvc.get("/api/seller/dashboard?from=${today.minusDays(7)}&to=${today.minusDays(1)}") { with(user(seller)) }.andExpect {
            jsonPath("$.data.orderCount") { value(0) }
            jsonPath("$.data.salesAmount") { value(0) }
            jsonPath("$.data.unsettledAmount") { value(200_000) }
        }

        mockMvc.post("/api/admin/settlements") { with(user("admin").roles("ADMIN")); with(csrf()) }
            .andExpect { status { isOk() } }

        // 기간 생략 시 오늘까지 최근 30일. 정산서가 생기면 미정산 → 지급대기(수수료 10% 차감)로 옮겨간다
        mockMvc.get("/api/seller/dashboard") { with(user(seller)) }.andExpect {
            jsonPath("$.data.to") { value(today.toString()) }
            jsonPath("$.data.orderCount") { value(1) }
            jsonPath("$.data.unsettledAmount") { value(0) }
            jsonPath("$.data.pendingPayoutAmount") { value(180_000) }
        }
    }
}
