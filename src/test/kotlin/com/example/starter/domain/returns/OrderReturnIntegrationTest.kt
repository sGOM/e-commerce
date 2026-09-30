package com.example.starter.domain.returns

import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.delivery.ShippingPolicyService
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.payment.entity.PaymentStatus
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.settlement.SettlementService
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.security.userdetails.CustomUserDetails
import com.example.starter.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@AutoConfigureMockMvc
@Transactional
class OrderReturnIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var subOrderRepository: SubOrderRepository
    @Autowired lateinit var paymentRepository: PaymentRepository
    @Autowired lateinit var shippingPolicyService: ShippingPolicyService
    @Autowired lateinit var settlementService: SettlementService
    @Autowired lateinit var em: EntityManager

    private val address = """"shippingAddress":{"receiverName":"수령","receiverPhone":"010-9","zipcode":"12345","address1":"서울 1"}"""

    private fun seedSeller(sku: String): Pair<CustomUserDetails, Long> {
        val u = User(email = "$sku@seller.com", password = "{noop}x", name = sku)
        roleRepository.findByName("ROLE_SELLER")?.let { u.grantRole(it) }
        userRepository.save(u)
        val seller = sellerRepository.save(Seller(userId = u.id!!, storeName = sku, status = SellerStatus.ACTIVE))
        val product = Product(seller = seller, name = "상품-$sku", basePrice = 20_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = sku, additionalPrice = 0)
        option.assignInventory(Inventory(quantity = 10, reserved = 0))
        product.addOption(option)
        return CustomUserDetails(u) to productRepository.save(product).options.first().id!!
    }

    private fun seedBuyer(email: String): CustomUserDetails =
        CustomUserDetails(userRepository.save(User(email = email, password = "{noop}x", name = email)))

    /** 주문·결제 후 판매자가 발송까지 마친 SubOrder id(SHIPPED). */
    private fun placeShippedOrder(buyer: CustomUserDetails, seller: CustomUserDetails, optionId: Long): Long {
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
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        val subOrderId = orderRepository.findById(orderId).get().subOrders.first().id!!
        mockMvc.post("/api/seller/orders/$subOrderId/ship") {
            with(user(seller)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ","trackingNumber":"T-$subOrderId"}"""
        }.andExpect { status { isOk() } }
        return subOrderId
    }

    private fun requestReturn(buyer: CustomUserDetails, subOrderId: Long, reason: String = "CHANGE_OF_MIND") =
        mockMvc.post("/api/me/returns") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"subOrderId":$subOrderId,"reason":"$reason","detail":"사이즈가 안 맞아요"}"""
        }

    private fun returnIdOf(body: String): Long = Regex(""""returnId":(\d+)""").find(body)!!.groupValues[1].toLong()

    private fun sellerAction(seller: CustomUserDetails, returnId: Long, action: String) =
        mockMvc.post("/api/seller/returns/$returnId/$action") { with(user(seller)); with(csrf()) }

    @Test
    fun `단순변심 반품은 요청-회수-검수 완료 후 반품 배송비를 뺀 금액을 환불한다`() {
        val (seller, optionId) = seedSeller("RET-1")
        val buyer = seedBuyer("ret-buyer-1@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        val payableShare = subOrderRepository.findById(subOrderId).get().payableShare
        val returnFee = shippingPolicyService.returnFee()

        val body = requestReturn(buyer, subOrderId).andExpect {
            status { isOk() }
            jsonPath("$.data.status") { value("REQUESTED") }
            jsonPath("$.data.returnFee") { value(returnFee) }
            jsonPath("$.data.refundAmount") { value(payableShare - returnFee) }
        }.andReturn().response.contentAsString
        val returnId = returnIdOf(body)
        assertEquals(SubOrderStatus.RETURNING, subOrderRepository.findById(subOrderId).get().status)

        sellerAction(seller, returnId, "approve").andExpect { jsonPath("$.data.status") { value("COLLECTING") } }
        sellerAction(seller, returnId, "complete").andExpect { jsonPath("$.data.status") { value("COMPLETED") } }

        em.flush(); em.clear()
        val subOrder = subOrderRepository.findById(subOrderId).get()
        assertEquals(SubOrderStatus.RETURNED, subOrder.status)
        // 유일한 하위 주문이 반품되면 주문 전체가 종료되고 결제도 취소된다
        assertEquals(OrderStatus.CANCELED, subOrder.order.status)
        val payment = paymentRepository.findByOrderId(subOrder.order.id!!).get()
        assertEquals(PaymentStatus.CANCELED, payment.status)
        assert(payment.events.any { it.detail?.contains("환불 ${payableShare - returnFee}원") == true })
    }

    private fun reservedOf(optionId: Long): Int {
        em.flush(); em.clear()
        return productRepository.findWithDetailById(
            productRepository.findAll().first { p -> p.options.any { it.id == optionId } }.id!!,
        ).get().options.first { it.id == optionId }.inventory!!.reserved
    }

    @Test
    fun `부분 반품 후 관리자 전체 환불은 반품된 하위 주문을 다시 환불하거나 재고를 복원하지 않는다`() {
        val (sellerA, optionA) = seedSeller("RET-10-A")
        val (_, optionB) = seedSeller("RET-10-B")
        val buyer = seedBuyer("ret-buyer-10@example.com")
        val other = seedBuyer("ret-other-10@example.com")
        // 다른 구매자의 A 예약 1개 — 이중 복원이 reserved >= qty 가드에 가려지지 않게 한다
        mockMvc.post("/api/cart/items") {
            with(user(other)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"optionId":$optionA,"quantity":1}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/orders") {
            with(user(other)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"o@e.com",$address}"""
        }.andExpect { status { isOk() } }

        listOf(optionA, optionB).forEach { optionId ->
            mockMvc.post("/api/cart/items") {
                with(user(buyer)); with(csrf())
                contentType = MediaType.APPLICATION_JSON
                content = """{"optionId":$optionId,"quantity":1}"""
            }.andExpect { status { isOk() } }
        }
        val res = mockMvc.post("/api/orders") {
            with(user(buyer)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"ordererName":"구매","ordererPhone":"010-1","ordererEmail":"b@e.com",$address}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val orderId = Regex(""""orderId":(\d+)""").find(res)!!.groupValues[1].toLong()
        mockMvc.post("/api/payments/$orderId") { with(user(buyer)); with(csrf()) }.andExpect { status { isOk() } }
        val order = orderRepository.findById(orderId).get()
        val subA = order.subOrders.first { sub -> sub.items.any { it.optionId == optionA } }
        val subB = order.subOrders.first { it.id != subA.id }
        val subAId = subA.id!!
        val shareA = subA.payableShare
        val shareB = subB.payableShare
        mockMvc.post("/api/seller/orders/$subAId/ship") {
            with(user(sellerA)); with(csrf())
            contentType = MediaType.APPLICATION_JSON
            content = """{"courier":"CJ","trackingNumber":"T-$subAId"}"""
        }.andExpect { status { isOk() } }
        assertEquals(2, reservedOf(optionA))

        val returnId = returnIdOf(requestReturn(buyer, subAId).andReturn().response.contentAsString)
        sellerAction(sellerA, returnId, "approve").andExpect { status { isOk() } }
        sellerAction(sellerA, returnId, "complete").andExpect { status { isOk() } }

        // 단순변심 반품은 재고로 돌아오고, B 가 남아 있어 주문·결제는 PAID 유지(부분 환불 기록)
        assertEquals(1, reservedOf(optionA))
        assertEquals(OrderStatus.PAID, orderRepository.findById(orderId).get().status)
        val payment = paymentRepository.findByOrderId(orderId).get()
        assertEquals(PaymentStatus.PAID, payment.status)
        val returnFee = shippingPolicyService.returnFee()
        assert(payment.events.any { it.detail == "부분 환불 ${shareA - returnFee}원" })

        mockMvc.post("/api/admin/orders/$orderId/refund") { with(user("admin").roles("ADMIN")); with(csrf()) }
            .andExpect { status { isOk() } }

        assertEquals(1, reservedOf(optionA)) // 반품된 A 는 다시 복원하지 않는다
        assertEquals(0, reservedOf(optionB))
        val events = paymentRepository.findByOrderId(orderId).get().events
        assertEquals(PaymentStatus.CANCELED, paymentRepository.findByOrderId(orderId).get().status)
        assert(events.any { it.detail == "관리자 환불 (환불 ${shareB}원)" }) { events.map { it.detail } }
    }

    @Test
    fun `반품 완료 건은 단순변심 반품 배송비만 판매자에게 정산되고 불량 반품은 정산할 것이 없다`() {
        val (seller, optionId) = seedSeller("RET-11")
        val (defectSeller, defectOption) = seedSeller("RET-11-D")
        val buyer = seedBuyer("ret-buyer-11@example.com")
        val returnFee = shippingPolicyService.returnFee()
        listOf(
            Triple(seller, optionId, "CHANGE_OF_MIND"),
            Triple(defectSeller, defectOption, "DEFECTIVE"),
        ).forEach { (s, option, reason) ->
            val subOrderId = placeShippedOrder(buyer, s, option)
            val returnId = returnIdOf(requestReturn(buyer, subOrderId, reason).andReturn().response.contentAsString)
            sellerAction(s, returnId, "approve").andExpect { status { isOk() } }
            sellerAction(s, returnId, "complete").andExpect { status { isOk() } }
        }

        val settlements = settlementService.generate()

        // 판매자가 자기 택배사로 회수했으므로 고객이 낸 반품 배송비는 판매자 몫(수수료 없음), 상품 판매액은 0
        val mine = settlements.single { it.storeName == "RET-11" }
        assertEquals(0, mine.salesAmount)
        assertEquals(returnFee, mine.deliveryFeeAmount)
        assertEquals(returnFee, mine.payoutAmount)
        // 불량 반품은 판매자 부담 — 받을 돈이 없어 정산서도 없다
        assert(settlements.none { it.storeName == "RET-11-D" })
    }

    @Test
    fun `상품 불량 반품은 반품 배송비 없이 전액 환불한다`() {
        val (seller, optionId) = seedSeller("RET-2")
        val buyer = seedBuyer("ret-buyer-2@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        val payableShare = subOrderRepository.findById(subOrderId).get().payableShare

        requestReturn(buyer, subOrderId, "DEFECTIVE").andExpect {
            jsonPath("$.data.returnFee") { value(0) }
            jsonPath("$.data.refundAmount") { value(payableShare) }
        }
    }

    @Test
    fun `판매자가 반품을 거절하면 하위 주문이 원래 상태로 돌아간다`() {
        val (seller, optionId) = seedSeller("RET-3")
        val buyer = seedBuyer("ret-buyer-3@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        val returnId = returnIdOf(requestReturn(buyer, subOrderId).andReturn().response.contentAsString)

        sellerAction(seller, returnId, "reject").andExpect { jsonPath("$.data.status") { value("REJECTED") } }

        assertEquals(SubOrderStatus.SHIPPED, subOrderRepository.findById(subOrderId).get().status)
        // 거절 후 다시 요청할 수 있다. 실제로는 별도 트랜잭션이므로 거절(UPDATE)을 먼저 반영한다
        // (한 트랜잭션에선 Hibernate 가 INSERT 를 UPDATE 보다 먼저 flush 해 진행 중 반품 유니크 인덱스에 걸린다).
        em.flush()
        requestReturn(buyer, subOrderId).andExpect { status { isOk() } }
    }

    @Test
    fun `진행 중인 반품이 있으면 다시 요청할 수 없다`() {
        val (seller, optionId) = seedSeller("RET-4")
        val buyer = seedBuyer("ret-buyer-4@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        requestReturn(buyer, subOrderId).andExpect { status { isOk() } }

        requestReturn(buyer, subOrderId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RETURN-002") }
        }
    }

    @Test
    fun `구매확정 후 반품 기간이 지나면 요청할 수 없다`() {
        val (seller, optionId) = seedSeller("RET-5")
        val buyer = seedBuyer("ret-buyer-5@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        val subOrder = subOrderRepository.findById(subOrderId).get()
        subOrder.confirmDelivery()
        subOrder.deliveredAt = Instant.now().minus(Duration.ofDays(shippingPolicyService.returnWindowDays() + 1L))
        em.flush()

        requestReturn(buyer, subOrderId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RETURN-003") }
        }
    }

    @Test
    fun `이미 정산된 하위 주문은 반품을 요청할 수 없다`() {
        val (seller, optionId) = seedSeller("RET-6")
        val buyer = seedBuyer("ret-buyer-6@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        settlementService.generate()

        requestReturn(buyer, subOrderId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RETURN-004") }
        }
    }

    @Test
    fun `발송 전 하위 주문은 반품이 아니라 취소 대상이다`() {
        val (seller, optionId) = seedSeller("RET-7")
        val buyer = seedBuyer("ret-buyer-7@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        subOrderRepository.findById(subOrderId).get().status = SubOrderStatus.PAID
        em.flush()

        requestReturn(buyer, subOrderId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("RETURN-002") }
        }
    }

    @Test
    fun `남의 하위 주문 반품 요청과 남의 반품 처리는 404`() {
        val (seller, optionId) = seedSeller("RET-8")
        val (otherSeller, _) = seedSeller("RET-8-OTHER")
        val buyer = seedBuyer("ret-buyer-8@example.com")
        val stranger = seedBuyer("ret-stranger-8@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)

        requestReturn(stranger, subOrderId).andExpect { status { isNotFound() } }
        val returnId = returnIdOf(requestReturn(buyer, subOrderId).andReturn().response.contentAsString)
        sellerAction(otherSeller, returnId, "approve").andExpect { status { isNotFound() } }
    }

    @Test
    fun `구매자와 판매자는 각자 반품 목록을 조회한다`() {
        val (seller, optionId) = seedSeller("RET-9")
        val buyer = seedBuyer("ret-buyer-9@example.com")
        val subOrderId = placeShippedOrder(buyer, seller, optionId)
        requestReturn(buyer, subOrderId).andExpect { status { isOk() } }

        mockMvc.get("/api/me/returns") { with(user(buyer)) }.andExpect {
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].subOrderId") { value(subOrderId) }
        }
        mockMvc.get("/api/seller/returns") { with(user(seller)) }.andExpect {
            jsonPath("$.data.length()") { value(1) }
            jsonPath("$.data[0].reason") { value("CHANGE_OF_MIND") }
        }
    }
}
