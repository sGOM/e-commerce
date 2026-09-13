package com.example.starter.domain.loyalty

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.loyalty.entity.LoyaltyTier
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
import org.springframework.transaction.annotation.Transactional

/**
 * 로열티 등급 재계산 배치 — 최근 12개월 순구매액(실결제, 취소 제외) 기준 등급 판정을 검증한다.
 * 기본 임계값(`LoyaltyTierProperties`): SILVER 30만/GOLD 100만/VIP 300만.
 */
@AutoConfigureMockMvc
@Transactional
class LoyaltyTierBatchServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var loyaltyTierBatchService: LoyaltyTierBatchService
    @Autowired lateinit var loyaltyTierService: LoyaltyTierService

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String, basePrice: Long): Long {
        val sellerUser = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = sellerUser.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun placePaidOrder(buyer: CustomUserDetails, optionId: Long, quantity: Int) {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":$quantity}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"loyalty-b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
    }

    @Test
    fun `순구매액이 GOLD 임계값 이상이면 GOLD 등급으로 판정된다`() {
        val buyer = seedBuyer("loyalty-buyer-1@example.com")
        val optionId = seedOption("LOY-SKU-1", 500_000)
        placePaidOrder(buyer, optionId, 3) // 150만원 결제 → GOLD(100만) 이상, VIP(300만) 미만

        loyaltyTierBatchService.recalculateAll()

        val myTier = loyaltyTierService.getMyTier(buyer.userId)
        assertEquals(LoyaltyTier.GOLD, myTier.tier)
        assertEquals(1_500_000, myTier.netPurchaseAmount12m)
        assertEquals(LoyaltyTier.VIP, myTier.nextTier)
        assertEquals(1_500_000, myTier.amountToNextTier) // VIP 임계값(300만) - 현재(150만)
    }

    @Test
    fun `구매 이력이 없으면 BRONZE 기본 등급이다`() {
        val buyer = seedBuyer("loyalty-buyer-2@example.com")

        loyaltyTierBatchService.recalculateAll()

        val myTier = loyaltyTierService.getMyTier(buyer.userId)
        assertEquals(LoyaltyTier.BRONZE, myTier.tier)
        assertEquals(0, myTier.netPurchaseAmount12m)
    }

    /**
     * 등급 쿠폰 매핑(`loyalty-coupon.couponIdByTier`)이 비어있는 기본 설정에서도 승급 처리 자체는
     * 예외 없이 끝까지 완료된다(운영에서 쿠폰을 아직 안 만든 상태에 안전 — [LoyaltyTierBenefitService]
     * 가 조용히 스킵). 실제 쿠폰 발급 멱등성 검증은 [LoyaltyTierBenefitServiceIntegrationTest] 참고.
     */
    @Test
    fun `쿠폰 매핑이 없어도 등급 승급 배치는 정상 완료된다`() {
        val buyer = seedBuyer("loyalty-buyer-3@example.com")
        val optionId = seedOption("LOY-SKU-3", 500_000)
        placePaidOrder(buyer, optionId, 3) // GOLD 승급

        val result = loyaltyTierBatchService.recalculateAll()

        assertEquals(0, result.erroredCount)
        assertEquals(LoyaltyTier.GOLD, loyaltyTierService.getMyTier(buyer.userId).tier)
    }
}
