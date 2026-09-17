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
    fun `판매자 상품 목록은 비공개 상태를 포함해 본인 상품만 반환한다`() {
        val mine = seedSeller("list-mine@example.com")
        val other = seedSeller("list-other@example.com")
        mockMvc.post("/api/seller/products") {
            with(user(mine)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"임시저장","basePrice":1000,"status":"DRAFT","options":[{"name":"기본","sku":"LIST-SKU-1","stockQuantity":3}]}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/seller/products") {
            with(user(other)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"남의상품","basePrice":1000,"status":"ON_SALE","options":[{"name":"기본","sku":"LIST-SKU-2","stockQuantity":3}]}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/seller/products") {
            with(user(mine))
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].name") { value("임시저장") }
            jsonPath("$.data[0].status") { value("DRAFT") }
            jsonPath("$.data[0].options[0].available") { value(3) }
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

    private fun createProduct(seller: CustomUserDetails, sku: String, stock: Int) {
        mockMvc.post("/api/seller/products") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"상품-$sku","basePrice":1000,"status":"ON_SALE","options":[{"name":"기본","sku":"$sku","additionalPrice":0,"stockQuantity":$stock}]}"""
        }.andExpect { status { isOk() } }
    }

    private fun bulkStock(seller: CustomUserDetails, items: String) = mockMvc.patch("/api/seller/products/stock") {
        with(user(seller)); with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = """{"items":$items}"""
    }

    @Test
    fun `판매자는 SKU 목록으로 여러 옵션 재고를 한 번에 설정한다`() {
        val seller = seedSeller("bulk-seller@example.com")
        createProduct(seller, "BULK-A", 1)
        createProduct(seller, "BULK-B", 2)

        bulkStock(seller, """[{"sku":"BULK-A","quantity":30},{"sku":"BULK-B","quantity":0}]""").andExpect {
            status { isOk() }
            jsonPath("$.data.updated") { value(2) }
        }
        mockMvc.get("/api/seller/products") { with(user(seller)) }.andExpect {
            jsonPath("$.data[?(@.name == '상품-BULK-A')].options[0].quantity") { value(30) }
            jsonPath("$.data[?(@.name == '상품-BULK-B')].options[0].quantity") { value(0) }
        }
    }

    @Test
    fun `모르는 SKU 나 남의 SKU 가 하나라도 있으면 아무것도 바꾸지 않는다`() {
        val seller = seedSeller("bulk-seller2@example.com")
        val other = seedSeller("bulk-other@example.com")
        createProduct(seller, "BULK-C", 5)
        createProduct(other, "BULK-OTHER", 5)

        bulkStock(seller, """[{"sku":"BULK-C","quantity":9},{"sku":"BULK-OTHER","quantity":1},{"sku":"NOPE","quantity":1}]""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("COMMON-002") }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("BULK-OTHER")) }
            jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("NOPE")) }
        }
        mockMvc.get("/api/seller/products") { with(user(seller)) }.andExpect {
            jsonPath("$.data[0].options[0].quantity") { value(5) }
        }
    }
}
