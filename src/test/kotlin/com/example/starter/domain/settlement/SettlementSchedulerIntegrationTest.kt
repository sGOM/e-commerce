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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/**
 * 스케줄러 빈을 등록(`settlement.scheduler.enabled=true`)한 뒤 `run()` 을 직접 호출해
 * 주기 자동 정산이 미정산 주문을 정산하고 멱등(2회차 0건)함을 검증한다. cron 타이밍에 의존하지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["settlement.scheduler.enabled=true"])
class SettlementSchedulerIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var scheduler: SettlementScheduler
    @Autowired lateinit var settlementService: SettlementService

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedSeller(sku: String, basePrice: Long): Long {
        val u = User(email = "$sku@seller.com", password = "{noop}x", name = sku)
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = "store-$sku", status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
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
        }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }
    }

    @Test
    fun `스케줄러 실행이 미정산 주문을 정산하고 재실행 시 추가 정산이 없다`() {
        val optionId = seedSeller("SCH-1", basePrice = 100_000)
        placePaidOrder(seedBuyer("sch-buyer-1@example.com"), optionId, 2) // 판매액 200,000

        scheduler.run()
        val seller = sellerRepository.findAll().first { it.storeName == "store-SCH-1" }
        val after = settlementService.getSellerSettlements(seller.userId)
        assertThat(after).hasSize(1)
        assertThat(after.first().salesAmount).isEqualTo(200_000)
        assertThat(after.first().payoutAmount).isEqualTo(180_000) // 기본 수수료 10%

        // 재실행: 미정산 대상 없음 → 정산서가 늘지 않는다(멱등).
        scheduler.run()
        assertThat(settlementService.getSellerSettlements(seller.userId)).hasSize(1)
    }
}
