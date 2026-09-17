package com.example.starter.domain.catalog

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.event.InventoryReservedEvent
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/** 기본 임계치(inventory.low-stock-threshold) 5 기준. */
@AutoConfigureMockMvc
@RecordApplicationEvents
@Transactional
class LowStockAlertIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var events: ApplicationEvents
    @Autowired lateinit var lowStockAlertService: LowStockAlertService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var notificationRepository: NotificationRepository

    private fun seed(sku: String, quantity: Int, reserved: Int): Pair<Long, Long> {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = "store-$sku", status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 1000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = quantity, reserved = reserved))
        product.addOption(option)
        return u.id!! to productRepository.save(product).options.first().id!!
    }

    private fun alerts(userId: Long) =
        notificationRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(0, 10)).content
            .filter { it.type == NotificationType.LOW_STOCK }

    @Test
    fun `예약으로 가용재고가 임계치 이하로 내려가면 판매자에게 알린다`() {
        val (sellerUserId, optionId) = seed("LOW-1", quantity = 10, reserved = 6) // 예약 3 → 가용 7 에서 4 로

        lowStockAlertService.onReserved(optionId, 3)

        assertThat(alerts(sellerUserId)).hasSize(1)
        assertThat(alerts(sellerUserId).first().body).contains("기본").contains("4개")
    }

    @Test
    fun `이미 임계치 이하였거나 예약 후에도 임계치보다 많으면 알리지 않는다`() {
        val (alreadyLowUser, alreadyLow) = seed("LOW-2", quantity = 10, reserved = 7) // 예약 1 → 4 에서 3 으로
        val (plentyUser, plenty) = seed("LOW-3", quantity = 100, reserved = 1) // 예약 1 → 100 에서 99 로

        lowStockAlertService.onReserved(alreadyLow, 1)
        lowStockAlertService.onReserved(plenty, 1)

        assertThat(alerts(alreadyLowUser)).isEmpty()
        assertThat(alerts(plentyUser)).isEmpty()
    }

    @Test
    fun `주문 생성 시 재고 예약 이벤트가 발행된다`() {
        val (_, optionId) = seed("LOW-4", quantity = 10, reserved = 0)
        val buyer = CustomUserDetails(userRepository.save(User(email = "low-buyer@ex.com", password = "{noop}x", name = "구매")))
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":2}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",""" +
                """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}}"""
        }.andExpect { status { isOk() } }

        assertThat(events.stream(InventoryReservedEvent::class.java)).containsExactly(InventoryReservedEvent(optionId, 2))
    }
}
