package com.example.starter.domain.auth

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.membership.entity.Membership
import com.example.starter.domain.membership.entity.MembershipBillingKey
import com.example.starter.domain.membership.entity.MembershipStatus
import com.example.starter.domain.membership.repository.MembershipBillingKeyRepository
import com.example.starter.domain.membership.repository.MembershipRepository
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.ShippingAddress
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.subscription.entity.DeliverySubscription
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import com.example.starter.domain.subscription.repository.DeliverySubscriptionRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.entity.UserStatus
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional

/** 회원 탈퇴(ROADMAP 3.3, soft delete). */
@AutoConfigureMockMvc
@Transactional
class WithdrawalIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var passwordEncoder: PasswordEncoder
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var membershipRepository: MembershipRepository
    @Autowired lateinit var membershipBillingKeyRepository: MembershipBillingKeyRepository
    @Autowired lateinit var deliverySubscriptionRepository: DeliverySubscriptionRepository

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun member(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = passwordEncoder.encode("Passw0rd!"), name = email)))

    private fun seedOption(sku: String): Long {
        val u = userRepository.save(User(email = "$sku@seller.com", password = "{noop}x", name = sku))
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        return productRepository.save(product).options.first().id!!
    }

    private fun placeOrder(buyer: CustomUserDetails, optionId: Long, pay: Boolean): Long {
        mockMvc.post("/api/cart/items") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionId,"quantity":1}"""
        }.andExpect { status { isOk() } }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        if (pay) mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        return orderId
    }

    private fun withdraw(principal: CustomUserDetails, password: String? = "Passw0rd!") = mockMvc.post("/api/auth/withdraw") {
        with(user(principal)); with(csrf())
        contentType = MediaType.APPLICATION_JSON
        content = if (password == null) "{}" else """{"password":"$password"}"""
    }

    @Test
    fun `탈퇴하면 계정은 남긴 채 WITHDRAWN 으로 바뀌고 미결제 주문·멤버십·정기배송·결제수단이 정리된다`() {
        val me = member("wd-1@example.com")
        val optionId = seedOption("WD1")
        val unpaidOrder = placeOrder(me, optionId, pay = false)
        membershipRepository.save(Membership(userId = me.userId))
        membershipBillingKeyRepository.save(MembershipBillingKey(userId = me.userId, gatewayBillingKey = "bk", cardLast4 = "1234"))
        val subscription = deliverySubscriptionRepository.save(
            DeliverySubscription(
                userId = me.userId, optionId = optionId, quantity = 1, cycleDays = 7,
                ordererName = "구매", ordererPhone = "010-1", ordererEmail = "b@e.com",
                shippingAddress = ShippingAddress("수령", "010-9", "12345", "서울 1", null),
            ),
        )

        withdraw(me).andExpect { status { isOk() } }

        val user = userRepository.findById(me.userId).get()
        assertEquals(UserStatus.WITHDRAWN, user.status)
        assertNotNull(user.withdrawnAt)
        assertEquals(OrderStatus.CANCELED, orderRepository.findById(unpaidOrder).get().status)
        assertEquals(MembershipStatus.CANCELED, membershipRepository.findByUserId(me.userId)!!.status)
        assertNull(membershipBillingKeyRepository.findByUserId(me.userId))
        assertEquals(DeliverySubscriptionStatus.CANCELED, deliverySubscriptionRepository.findById(subscription.id!!).get().status)

        // 탈퇴 계정은 로그인할 수 없다(존재 여부를 흘리지 않는 단일 메시지)
        mockMvc.post("/api/auth/login") {
            with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"wd-1@example.com","password":"Passw0rd!"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `비밀번호가 틀리면 탈퇴되지 않는다`() {
        val me = member("wd-2@example.com")

        withdraw(me, "wrong").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("USER-004") }
        }
        assertEquals(UserStatus.ACTIVE, userRepository.findById(me.userId).get().status)
    }

    @Test
    fun `결제 후 배송이 끝나지 않은 주문이 있으면 탈퇴할 수 없다`() {
        val me = member("wd-3@example.com")
        placeOrder(me, seedOption("WD3"), pay = true)

        withdraw(me).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USER-005") }
        }
    }

    @Test
    fun `판매자는 스스로 탈퇴할 수 없다`() {
        val sellerUser = userRepository.save(User(email = "wd-4@example.com", password = passwordEncoder.encode("Passw0rd!"), name = "s"))
        sellerUser.grantRole(roleRepository.findByName("ROLE_SELLER")!!)

        withdraw(CustomUserDetails(sellerUser)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("USER-006") }
        }
    }

    @Test
    fun `소셜 전용 계정은 비밀번호 없이 탈퇴한다`() {
        val social = CustomUserDetails(userRepository.save(User(email = "wd-5@example.com", password = null, name = "s")))

        withdraw(social, password = null).andExpect { status { isOk() } }
        assertEquals(UserStatus.WITHDRAWN, userRepository.findById(social.userId).get().status)
    }
}
