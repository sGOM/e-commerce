package com.example.starter.domain.admin

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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
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
class AdminDashboardIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository

    private val today = LocalDate.now(ZoneId.of("Asia/Seoul"))

    private fun dashboard(from: LocalDate, to: LocalDate): JsonNode {
        val body = mockMvc.get("/api/admin/dashboard?from=$from&to=$to") {
            with(user("admin").roles("ADMIN"))
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body)["data"]
    }

    private fun seedOption(sku: String, basePrice: Long): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = "store-$sku", status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private fun placeOrder(optionIds: List<Long>, pay: Boolean) {
        val buyer = CustomUserDetails(
            userRepository.save(User(email = "adm-dash-${System.nanoTime()}@ex.com", password = "{noop}x", name = "구매")),
        )
        optionIds.forEach { optionId ->
            mockMvc.post("/api/cart/items") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionId":$optionId,"quantity":1}"""
            }.andExpect { status { isOk() } }
        }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",""" +
                """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        if (pay) {
            val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
            mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        }
    }

    @Test
    fun `결제된 주문만 GMV 와 주문 수에 잡히고 신규 가입과 일별 추이를 함께 준다`() {
        val a = seedOption("ADM-DASH-A", 10_000)
        val b = seedOption("ADM-DASH-B", 5_000)
        val before = dashboard(today, today)

        placeOrder(listOf(a, b), pay = true) // 판매자 2곳, 주문 1건, 15,000
        placeOrder(listOf(a), pay = false) // 미결제 — 제외

        val after = dashboard(today, today)
        assertThat(after["orderCount"].asLong() - before["orderCount"].asLong()).isEqualTo(1)
        assertThat(after["gmv"].asLong() - before["gmv"].asLong()).isEqualTo(15_000)
        // before 이후 가입한 구매자 2명
        assertThat(after["newUserCount"].asLong() - before["newUserCount"].asLong()).isEqualTo(2)
        assertThat(after["daily"]).hasSize(1)
        assertThat(after["daily"][0]["date"].asText()).isEqualTo(today.toString())
        assertThat(after["daily"][0]["gmv"].asLong()).isEqualTo(after["gmv"].asLong())
    }

    @Test
    fun `일별 추이는 주문이 없는 날도 0으로 채운다`() {
        val data = dashboard(today.minusDays(400), today.minusDays(394))

        assertThat(data["daily"]).hasSize(7)
        assertThat(data["daily"].all { it["orderCount"].asLong() == 0L && it["gmv"].asLong() == 0L }).isTrue()
    }

    @Test
    fun `시작일이 종료일보다 늦거나 기간이 1년을 넘으면 거부한다`() {
        mockMvc.get("/api/admin/dashboard?from=$today&to=${today.minusDays(1)}") {
            with(user("admin").roles("ADMIN"))
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/api/admin/dashboard?from=${today.minusDays(366)}&to=$today") {
            with(user("admin").roles("ADMIN"))
        }.andExpect { status { isBadRequest() } }
    }
}
