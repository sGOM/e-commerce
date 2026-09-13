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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class SettlementIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var roleRepository: RoleRepository

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** ROLE_SELLER 판매자 + 상품(옵션 1개). (판매자, optionId) 반환 */
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

    /** 구매자가 해당 옵션을 주문·결제(PAID)한다. 생성된 orderId 반환. */
    private fun placePaidOrder(buyer: CustomUserDetails, optionId: Long, quantity: Int): Long {
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
        return orderId
    }

    @Test
    fun `정산을 생성하면 판매자별로 수수료를 제하고 지급액이 계산된다`() {
        val (seller, optionId) = seedSeller("STL-1", basePrice = 100_000)
        val buyer = seedBuyer("stl-buyer-1@example.com")
        placePaidOrder(buyer, optionId, 2) // 판매액 200,000

        // 관리자 정산 생성 — 기본 수수료 10%
        mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].salesAmount") { value(200_000) }
            jsonPath("$.data[0].commissionAmount") { value(20_000) } // 10%
            jsonPath("$.data[0].payoutAmount") { value(180_000) }
            jsonPath("$.data[0].status") { value("PENDING") }
        }

        // 판매자 본인 정산 조회
        mockMvc.get("/api/seller/settlements") {
            with(user(seller))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].payoutAmount") { value(180_000) }
        }
    }

    @Test
    fun `이미 정산된 주문은 다시 정산되지 않는다`() {
        val (_, optionId) = seedSeller("STL-2", basePrice = 50_000)
        val buyer = seedBuyer("stl-buyer-2@example.com")
        placePaidOrder(buyer, optionId, 1)

        // 1차 정산 → 1건 생성
        mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
        }
        // 2차 정산 → 미정산 대상 없음 → 0건
        mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }

    @Test
    fun `관리자가 수수료율을 변경하고 지급 처리할 수 있다`() {
        // 수수료율 20%로 변경
        mockMvc.patch("/api/admin/settlements/policy") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"commissionRateBp":2000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.commissionRateBp") { value(2000) }
        }

        val (_, optionId) = seedSeller("STL-3", basePrice = 100_000)
        val buyer = seedBuyer("stl-buyer-3@example.com")
        placePaidOrder(buyer, optionId, 1) // 판매액 100,000

        val res = mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].commissionAmount") { value(20_000) } // 20%
            jsonPath("$.data[0].payoutAmount") { value(80_000) }
        }.andReturn().response.contentAsString
        val settlementId = Regex(""""settlementId":(\d+)""").find(res)!!.groupValues[1].toLong()

        mockMvc.patch("/api/admin/settlements/$settlementId/pay") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PAID") }
            jsonPath("$.data.paidAt") { exists() }
        }
    }

    @Test
    fun `관리자는 전체 정산 목록을 상태로 필터링해 조회한다`() {
        val (_, optionA) = seedSeller("STL-L1", basePrice = 10_000)
        val (_, optionB) = seedSeller("STL-L2", basePrice = 20_000)
        val buyer = seedBuyer("stl-buyer-l@example.com")
        placePaidOrder(buyer, optionA, 1)
        placePaidOrder(buyer, optionB, 1)
        val res = mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val paidId = Regex(""""settlementId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.patch("/api/admin/settlements/$paidId/pay") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/admin/settlements") {
            with(user("admin").roles("ADMIN"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(2) }
            jsonPath("$.data.content[0].storeName") { exists() }
        }
        mockMvc.get("/api/admin/settlements?status=PAID") {
            with(user("admin").roles("ADMIN"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].settlementId") { value(paidId) }
        }
        mockMvc.get("/api/admin/settlements") {
            with(user(seedBuyer("stl-not-admin@example.com")))
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `존재하지 않는 정산을 지급 처리하면 404를 반환한다`() {
        mockMvc.patch("/api/admin/settlements/999999/pay") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.code") { value("SETTLEMENT-001") }
        }
    }

    @Test
    fun `수수료율 한도(10000bp)를 넘기면 400을 반환한다`() {
        mockMvc.patch("/api/admin/settlements/policy") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"commissionRateBp":10001}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `판매자는 본인 상점의 정산만 조회한다`() {
        val (_, optionId) = seedSeller("STL-4", basePrice = 30_000)
        val buyer = seedBuyer("stl-buyer-4@example.com")
        placePaidOrder(buyer, optionId, 1)
        mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect { status { isOk() } }

        // 정산이 전혀 없는 다른 판매자는 빈 목록을 받는다.
        val (other, _) = seedSeller("STL-5", basePrice = 30_000)
        mockMvc.get("/api/seller/settlements") {
            with(user(other))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }

    @Test
    fun `취소된 주문은 정산 대상에서 제외된다`() {
        val (_, optionId) = seedSeller("STL-6", basePrice = 40_000)
        val buyer = seedBuyer("stl-buyer-6@example.com")
        val orderId = placePaidOrder(buyer, optionId, 1)

        // 결제 후 배송 전 전체 취소 → SubOrder 가 CANCELED 가 된다.
        mockMvc.post("/api/orders/$orderId/cancel") {
            with(user(buyer)); with(csrf())
        }.andExpect { status { isOk() } }

        // 정산 생성 시 취소건은 대상에서 빠져 0건.
        mockMvc.post("/api/admin/settlements") {
            with(user("admin").roles("ADMIN")); with(csrf())
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(0) }
        }
    }
}
