package com.example.starter.domain.seller

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
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class SellerBackofficeIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var roleRepository: RoleRepository

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** ROLE_SELLER 를 가진 승인 판매자 시드 + 사용자 반환 */
    private fun seedSeller(email: String): CustomUserDetails {
        val u = User(email = email, password = "{noop}x", name = email)
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        sellerRepository.save(
            com.example.starter.domain.seller.entity.Seller(
                userId = u.id!!,
                storeName = "store-$email",
                status = com.example.starter.domain.seller.entity.SellerStatus.ACTIVE,
            ),
        )
        return CustomUserDetails(u)
    }

    @Test
    fun `입점 신청 후 관리자가 승인하면 상점이 영업 상태가 된다`() {
        val applicant = seedMember("apply@example.com")

        val res = mockMvc.post("/api/seller/apply") {
            with(user(applicant)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"storeName":"내상점","description":"좋은 상점"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("PENDING") }
        }.andReturn().response.contentAsString
        val sellerId = Regex(""""sellerId":(\d+)""").find(res)!!.groupValues[1].toLong()

        // 관리자 목록(PENDING) 조회
        mockMvc.get("/api/admin/sellers?status=PENDING") {
            with(user("admin").roles("ADMIN"))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].sellerId") { value(sellerId) }
        }

        // 승인
        mockMvc.patch("/api/admin/sellers/$sellerId/approve") {
            with(user("admin").roles("ADMIN")); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"approved":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `중복 입점 신청은 거절된다`() {
        val applicant = seedMember("dup-apply@example.com")
        val body = """{"storeName":"상점"}"""
        mockMvc.post("/api/seller/apply") {
            with(user(applicant)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/seller/apply") {
            with(user(applicant)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SELLER-002") }
        }
    }

    @Test
    fun `판매자가 상품을 등록하고 재고를 조정한다`() {
        val seller = seedSeller("product-seller@example.com")

        val res = mockMvc.post("/api/seller/products") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"신상품","basePrice":10000,"status":"ON_SALE","options":[{"name":"기본","sku":"NEW-SKU-1","additionalPrice":0,"stockQuantity":5}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.name") { value("신상품") }
            jsonPath("$.data.options[0].available") { value(5) }
        }.andReturn().response.contentAsString
        val productId = Regex(""""productId":(\d+)""").find(res)!!.groupValues[1].toLong()
        val optionId = Regex(""""optionId":(\d+)""").find(res)!!.groupValues[1].toLong()

        // 재고 10으로 조정
        mockMvc.patch("/api/seller/products/$productId/stock") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":10}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.options[0].quantity") { value(10) }
        }
    }

    @Test
    fun `남의 상품은 수정할 수 없다`() {
        val sellerA = seedSeller("owner-seller@example.com")
        val sellerB = seedSeller("other-seller@example.com")

        val res = mockMvc.post("/api/seller/products") {
            with(user(sellerA)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"A상품","basePrice":10000,"options":[{"name":"기본","sku":"OWN-SKU-1","stockQuantity":1}]}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val productId = Regex(""""productId":(\d+)""").find(res)!!.groupValues[1].toLong()

        mockMvc.put("/api/seller/products/$productId") {
            with(user(sellerB)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"탈취","basePrice":1,"status":"HIDDEN"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CATALOG-001") }
        }
    }
}
