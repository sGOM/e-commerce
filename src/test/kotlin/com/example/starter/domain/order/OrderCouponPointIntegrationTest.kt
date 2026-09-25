package com.example.starter.domain.order

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.coupon.entity.Coupon
import com.example.starter.domain.coupon.entity.DiscountType
import com.example.starter.domain.coupon.entity.IssuedCoupon
import com.example.starter.domain.coupon.repository.CouponRepository
import com.example.starter.domain.coupon.repository.IssuedCouponRepository
import com.example.starter.domain.point.entity.PointAccount
import com.example.starter.domain.point.repository.PointAccountRepository
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
import java.time.Instant
import java.time.temporal.ChronoUnit

@AutoConfigureMockMvc
@Transactional
class OrderCouponPointIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var couponRepository: CouponRepository
    @Autowired lateinit var issuedCouponRepository: IssuedCouponRepository
    @Autowired lateinit var pointAccountRepository: PointAccountRepository

    private fun seedMember(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    private fun seedOption(sku: String, basePrice: Long = 10_000): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = basePrice, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 100, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private fun issueRateCoupon(userId: Long, percent: Long, minOrder: Long = 0): Long {
        val coupon = couponRepository.save(
            Coupon(
                name = "$percent% 할인",
                discountType = DiscountType.RATE,
                discountValue = percent,
                minOrderAmount = minOrder,
                validFrom = Instant.now().minus(1, ChronoUnit.DAYS),
                validUntil = Instant.now().plus(1, ChronoUnit.DAYS),
            ),
        )
        return issuedCouponRepository.save(IssuedCoupon(coupon = coupon, userId = userId)).id!!
    }

    private fun addToCart(buyer: CustomUserDetails, optionId: Long, quantity: Int) {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":$quantity}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `쿠폰과 포인트를 함께 적용해 결제금액을 계산한다`() {
        val buyer = seedMember("cp-buyer-1@example.com")
        val userId = buyer.userId
        // 보유 포인트 1,000 시드
        pointAccountRepository.save(
            PointAccount(userId = userId).apply {
                earn(1_000, null, java.time.Instant.now().plus(365, java.time.temporal.ChronoUnit.DAYS))
            },
        )
        val optionId = seedOption("CP-SKU-1", basePrice = 20_000)
        addToCart(buyer, optionId, 1) // 상품합계 20,000
        val couponId = issueRateCoupon(userId, percent = 10) // 10% → 2,000 할인

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"홍길동","ordererPhone":"010-1","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"홍","receiverPhone":"010","zipcode":"12345","address1":"서울 1"},"issuedCouponId":$couponId,"usePoint":1000}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.totalAmount") { value(20_000) }
            jsonPath("$.data.discountAmount") { value(2_000) }
            jsonPath("$.data.pointUsed") { value(1_000) }
            jsonPath("$.data.payableAmount") { value(20_000) } // 20000 - 2000 - 1000 + 배송비 3000
        }

        // 쿠폰 사용 처리, 포인트 차감 확인
        assertEquals(true, issuedCouponRepository.findById(couponId).get().used)
        assertEquals(0, pointAccountRepository.findByUserId(userId).get().balance)
    }

    @Test
    fun `보유보다 많은 포인트는 사용할 수 없다`() {
        val buyer = seedMember("cp-buyer-2@example.com")
        pointAccountRepository.save(
            PointAccount(userId = buyer.userId).apply {
                earn(500, null, java.time.Instant.now().plus(365, java.time.temporal.ChronoUnit.DAYS))
            },
        )
        val optionId = seedOption("CP-SKU-2", basePrice = 20_000)
        addToCart(buyer, optionId, 1)

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"홍","ordererPhone":"010","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"홍","receiverPhone":"010","zipcode":"12345","address1":"서울 1"},"usePoint":1000}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("POINT-001") }
        }
    }

    @Test
    fun `최소 주문금액 미달 쿠폰은 거절된다`() {
        val buyer = seedMember("cp-buyer-3@example.com")
        val optionId = seedOption("CP-SKU-3", basePrice = 5_000)
        addToCart(buyer, optionId, 1) // 합계 5,000
        val couponId = issueRateCoupon(buyer.userId, percent = 10, minOrder = 10_000)

        mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"홍","ordererPhone":"010","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"홍","receiverPhone":"010","zipcode":"12345","address1":"서울 1"},"issuedCouponId":$couponId}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("COUPON-004") }
        }
    }

    @Test
    fun `주문을 취소하면 쿠폰과 포인트가 복원된다`() {
        val buyer = seedMember("cp-buyer-4@example.com")
        val userId = buyer.userId
        pointAccountRepository.save(
            PointAccount(userId = userId).apply {
                earn(1_000, null, java.time.Instant.now().plus(365, java.time.temporal.ChronoUnit.DAYS))
            },
        )
        val optionId = seedOption("CP-SKU-4", basePrice = 20_000)
        addToCart(buyer, optionId, 1)
        val couponId = issueRateCoupon(userId, percent = 10)

        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"홍","ordererPhone":"010","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"홍","receiverPhone":"010","zipcode":"12345","address1":"서울 1"},"issuedCouponId":$couponId,"usePoint":1000}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()

        mockMvc.post("/api/orders/$orderId/cancel") {
            with(user(buyer)); with(csrf())
        }.andExpect { status { isOk() } }

        assertEquals(false, issuedCouponRepository.findById(couponId).get().used, "쿠폰 미사용 복원")
        assertEquals(1_000, pointAccountRepository.findByUserId(userId).get().balance, "사용 포인트 환원")
    }

    @Test
    fun `결제가 확정되면 정책 적립률만큼 포인트가 적립된다`() {
        val buyer = seedMember("cp-buyer-5@example.com")
        val userId = buyer.userId
        val optionId = seedOption("CP-SKU-5", basePrice = 50_000)
        addToCart(buyer, optionId, 1) // 결제금액 50,000

        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"홍","ordererPhone":"010","ordererEmail":"b@e.com","shippingAddress":{"receiverName":"홍","receiverPhone":"010","zipcode":"12345","address1":"서울 1"}}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()

        mockMvc.post("/api/payments/$orderId") {
            with(user(buyer)); with(csrf())
        }.andExpect { status { isOk() } }

        // 기본 정책 1%(100bp) → 50,000의 1% = 500 적립
        assertEquals(500, pointAccountRepository.findByUserId(userId).get().balance)
    }
}
