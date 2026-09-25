package com.example.starter.domain.upload

import com.example.starter.domain.catalog.entity.Product
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class ImageUploadIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository

    // 1x1 투명 PNG
    private val png = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    )

    private fun member() =
        CustomUserDetails(userRepository.save(User(email = "up-${System.nanoTime()}@ex.com", password = "{noop}x", name = "u")))

    @Test
    fun `회원이 이미지를 올리면 공개 URL 로 다시 받을 수 있다`() {
        val res = mockMvc.multipart("/api/uploads") {
            file(MockMultipartFile("file", "photo.png", "image/png", png))
            with(user(member())); with(csrf())
        }.andExpect {
            status { isOk() }
        }.andReturn().response.contentAsString
        val url = Regex(""""url":"([^"]+)"""").find(res)!!.groupValues[1]

        mockMvc.get(url).andExpect {
            status { isOk() }
            content { contentType(MediaType.IMAGE_PNG) }
            content { bytes(png) }
            header { string("X-Content-Type-Options", "nosniff") }
        }
    }

    @Test
    fun `이미지가 아닌 파일은 확장자와 무관하게 거부한다`() {
        mockMvc.multipart("/api/uploads") {
            file(MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".toByteArray()))
            with(user(member())); with(csrf())
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("UPLOAD-001") }
        }
    }

    @Test
    fun `서버가 만든 이름 형식이 아니면 저장소를 보지 않고 거부한다`() {
        listOf("photo.png", "00000000-0000-0000-0000-000000000000.svg").forEach { name ->
            mockMvc.get("/api/uploads/$name").andExpect { status { isNotFound() } }
        }
        // 인코딩된 경로 구분자는 보안 필터(StrictHttpFirewall)가 먼저 400 으로 막는다
        mockMvc.get("/api/uploads/..%2Fapplication.yml").andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `비로그인 사용자는 업로드할 수 없다`() {
        mockMvc.multipart("/api/uploads") {
            file(MockMultipartFile("file", "photo.png", "image/png", png))
            with(csrf())
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `판매자가 상품 대표 이미지를 지정하면 상세에 노출된다`() {
        val u = User(email = "img-${System.nanoTime()}@seller.com", password = "{noop}x", name = "s")
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = "img-${System.nanoTime()}", status = SellerStatus.ACTIVE))
        val product = productRepository.save(Product(seller = seller, name = "코트", basePrice = 1000))

        mockMvc.put("/api/seller/products/${product.id}") {
            with(user(CustomUserDetails(u))); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"코트","basePrice":1000,"status":"ON_SALE","imageUrl":"/api/uploads/a.png"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.imageUrl") { value("/api/uploads/a.png") }
        }

        mockMvc.get("/api/products/${product.id}").andExpect {
            jsonPath("$.data.imageUrl") { value("/api/uploads/a.png") }
        }
    }
}
