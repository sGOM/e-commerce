package com.example.starter.domain.cart

import com.example.starter.domain.cart.repository.CartRepository
import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
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
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 장바구니 이탈 리마인드 배치. 기본 정책(`CartReminderProperties.inactivityHours=24`)을 기준으로
 * 마지막 활동 시각을 직접 과거로 되돌려 이탈 상황을 재현한다.
 */
@AutoConfigureMockMvc
@Transactional
class CartReminderBatchServiceIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var cartRepository: CartRepository
    @Autowired lateinit var notificationRepository: NotificationRepository
    @Autowired lateinit var cartReminderBatchService: CartReminderBatchService
    @Autowired lateinit var em: EntityManager

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String): Long {
        val sellerUser = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = sellerUser.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private fun addToCart(buyer: CustomUserDetails, optionId: Long) {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
    }

    /** 마지막 활동 시각을 과거로 되돌려 "N시간 경과" 상황을 만든다(네이티브 UPDATE — 감사 컬럼 우회). */
    private fun rewindLastActivity(userId: Long, hoursAgo: Long) {
        em.flush()
        em.createNativeQuery("UPDATE carts SET last_activity_at = :at WHERE user_id = :userId")
            .setParameter("at", java.sql.Timestamp.from(Instant.now().minus(hoursAgo, ChronoUnit.HOURS)))
            .setParameter("userId", userId)
            .executeUpdate()
        em.clear()
    }

    @Test
    fun `24시간 이상 미결제 장바구니는 리마인드 알림을 1회만 받는다`() {
        val buyer = seedMember("cart-reminder-1@example.com")
        addToCart(buyer, seedOption("CR-SKU-1"))
        rewindLastActivity(buyer.userId, 25)

        val firstRun = cartReminderBatchService.sendReminders()
        assertEquals(1, firstRun.remindedCount)

        val notifications = notificationRepository.findByUserIdOrderByIdDesc(buyer.userId, PageRequest.of(0, 10))
        assertEquals(1, notifications.content.size)
        assertEquals(NotificationType.CART_REMINDER, notifications.content.first().type)

        // 같은 이탈 구간에 대해 다시 배치를 돌려도 중복 발송하지 않는다(dedup).
        val secondRun = cartReminderBatchService.sendReminders()
        assertEquals(0, secondRun.remindedCount)
        val notificationsAfterSecondRun = notificationRepository.findByUserIdOrderByIdDesc(buyer.userId, PageRequest.of(0, 10))
        assertEquals(1, notificationsAfterSecondRun.content.size)
    }

    @Test
    fun `최근 활동한 장바구니는 리마인드 대상이 아니다`() {
        val buyer = seedMember("cart-reminder-2@example.com")
        addToCart(buyer, seedOption("CR-SKU-2")) // lastActivityAt = now

        val result = cartReminderBatchService.sendReminders()
        assertEquals(0, result.remindedCount)
    }
}
