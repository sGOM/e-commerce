package com.example.starter.domain.wishlist

import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.notification.repository.NotificationRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

@AutoConfigureMockMvc
@Transactional
class WishlistIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var wishlistService: WishlistService

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedProduct(name: String, basePrice: Long): Product {
        val sellerUser = userRepository.save(User(email = "$name@seller.com", password = "{noop}x", name = name))
        val seller = sellerRepository.save(Seller(userId = sellerUser.id!!, storeName = name, status = SellerStatus.ACTIVE))
        return productRepository.save(
            Product(seller = seller, name = name, basePrice = basePrice, status = ProductStatus.ON_SALE),
        )
    }

    @Test
    fun `위시리스트에 담고 조회하고 뺄 수 있다`() {
        val buyer = seedMember("wish-buyer-1@example.com")
        val product = seedProduct("wish-product-1", 10_000)

        mockMvc.post("/api/me/wishlist") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId":${product.id}}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.productId") { value(product.id!!.toInt()) }
            jsonPath("$.data.baselinePrice") { value(10_000) }
        }

        mockMvc.get("/api/me/wishlist") { with(user(buyer)) }.andExpect {
            status { isOk() }
            jsonPath("$.data.content.length()") { value(1) }
        }

        mockMvc.delete("/api/me/wishlist/${product.id}") { with(user(buyer)); with(csrf()) }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/me/wishlist") { with(user(buyer)) }.andExpect {
            jsonPath("$.data.content.length()") { value(0) }
        }
    }

    @Test
    fun `같은 상품을 중복으로 담을 수 없다`() {
        val buyer = seedMember("wish-buyer-2@example.com")
        val product = seedProduct("wish-product-2", 10_000)

        mockMvc.post("/api/me/wishlist") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId":${product.id}}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/me/wishlist") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"productId":${product.id}}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("WISHLIST-002") }
        }
    }

    @Test
    fun `가격이 인하되면 찜한 회원에게 알림이 생성되고 baseline이 갱신된다`() {
        val buyer = seedMember("wish-buyer-3@example.com")
        val product = seedProduct("wish-product-3", 10_000)
        wishlistService.add(buyer.userId, product.id!!)

        // 셀러가 정가를 8,000원으로 인하했다고 가정(SellerProductService.update 트랜잭션 밖에서
        // notifyPriceDrop 을 직접 호출 — AFTER_COMMIT 비동기 리스너는 테스트 트랜잭션이 실제로
        // 커밋되지 않아 실행되지 않으므로, 트리거되는 핵심 로직을 직접 검증한다).
        product.basePrice = 8_000
        productRepository.save(product)
        wishlistService.notifyPriceDrop(product.id!!, 8_000)

        val notifications = notificationRepository.findByUserIdOrderByIdDesc(buyer.userId, org.springframework.data.domain.PageRequest.of(0, 10))
        assertEquals(1, notifications.content.size)
        assertEquals(NotificationType.PRICE_DROP, notifications.content.first().type)

        val myList = wishlistService.getMyWishlist(buyer.userId, false, org.springframework.data.domain.PageRequest.of(0, 10))
        val item = myList.content.first()
        assertEquals(8_000, item.baselinePrice) // 발송 후 baseline 갱신(재알림 가능)
        assertTrue(!item.isPriceDropped) // 방금 갱신되어 더 이상 "인하 표시" 대상 아님

        // 추가로 6,000원까지 더 내려가면 다시 알림 가능(재알림 원칙)
        product.basePrice = 6_000
        productRepository.save(product)
        wishlistService.notifyPriceDrop(product.id!!, 6_000)
        val notificationsAfterSecondDrop =
            notificationRepository.findByUserIdOrderByIdDesc(buyer.userId, org.springframework.data.domain.PageRequest.of(0, 10))
        assertEquals(2, notificationsAfterSecondDrop.content.size)
    }

    @Test
    fun `가격이 오르면 알림을 보내지 않는다`() {
        val buyer = seedMember("wish-buyer-4@example.com")
        val product = seedProduct("wish-product-4", 10_000)
        wishlistService.add(buyer.userId, product.id!!)

        product.basePrice = 12_000
        productRepository.save(product)
        // 인상은 상위 이벤트 리스너에서 걸러지지만, 서비스 메서드 자체도 baseline 초과 필터로 안전하다.
        wishlistService.notifyPriceDrop(product.id!!, 12_000)

        val notifications = notificationRepository.findByUserIdOrderByIdDesc(buyer.userId, org.springframework.data.domain.PageRequest.of(0, 10))
        assertEquals(0, notifications.content.size)
    }
}
