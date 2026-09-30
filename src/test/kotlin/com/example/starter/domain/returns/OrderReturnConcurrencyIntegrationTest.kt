package com.example.starter.domain.returns

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.ShippingAddress
import com.example.starter.domain.order.entity.SubOrder
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.returns.dto.CreateReturnRequest
import com.example.starter.domain.returns.entity.ReturnReason
import com.example.starter.domain.returns.repository.OrderReturnRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 같은 하위 주문에 반품 요청이 동시에 들어와도 하나만 받아들이고 나머지는 409(비즈니스 오류)로 끝난다.
 * 트랜잭션이 스레드 경계를 넘지 못하므로 비-Transactional 이며, 만든 주문·반품은 직접 지운다(발송 상태 주문이
 * 남으면 전역 집계인 정산 테스트에 섞인다).
 */
class OrderReturnConcurrencyIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var returnService: ReturnService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var orderRepository: OrderRepository
    @Autowired lateinit var subOrderRepository: SubOrderRepository
    @Autowired lateinit var orderReturnRepository: OrderReturnRepository

    private var orderId: Long? = null
    private var subOrderId: Long? = null

    @AfterEach
    fun cleanup() {
        val id = orderId ?: return
        orderReturnRepository.deleteAll(orderReturnRepository.findBySubOrderId(subOrderId!!))
        orderRepository.deleteById(id)
    }

    @Test
    fun `같은 하위 주문에 동시에 반품을 요청하면 하나만 받고 나머지는 409 다`() {
        val buyer = userRepository.save(User(email = "ret-conc-${System.nanoTime()}@example.com", password = "{noop}x", name = "구매"))
        val sellerUser = userRepository.save(User(email = "ret-conc-s-${System.nanoTime()}@example.com", password = "{noop}x", name = "판매"))
        val seller = sellerRepository.save(Seller(userId = sellerUser.id!!, storeName = "ret-conc", status = SellerStatus.ACTIVE))
        val order = Order(
            orderNumber = "ORD-RETCONC-${System.nanoTime()}",
            userId = buyer.id,
            ordererName = "구매",
            ordererPhone = "010-1",
            ordererEmail = "b@e.com",
            shippingAddress = ShippingAddress("수령", "010-9", "12345", "서울 1", null),
        )
        order.addSubOrder(
            SubOrder(seller = seller).apply {
                status = SubOrderStatus.SHIPPED
                subtotal = 10_000
                payableShare = 13_000
            },
        )
        val saved = orderRepository.save(order)
        orderId = saved.id
        val subOrderId = saved.subOrders.single().id!!
        this.subOrderId = subOrderId

        val contenders = 5
        val pool = Executors.newFixedThreadPool(contenders)
        val ready = CountDownLatch(contenders)
        val go = CountDownLatch(1)
        val results = (1..contenders).map {
            pool.submit(
                Callable {
                    ready.countDown()
                    go.await()
                    runCatching { returnService.request(buyer.id!!, CreateReturnRequest(subOrderId, ReturnReason.DEFECTIVE)) }
                },
            )
        }
        ready.await()
        go.countDown()
        val outcomes = results.map { it.get() }
        pool.shutdown()

        assertThat(outcomes.count { it.isSuccess }).isEqualTo(1)
        // 실패는 전부 "이미 반품 진행 중" 비즈니스 오류여야 한다(유니크 위반 500 이 아니라)
        assertThat(outcomes.filter { it.isFailure }.map { (it.exceptionOrNull() as? BusinessException)?.errorCode })
            .containsOnly(ErrorCode.SUB_ORDER_NOT_RETURNABLE)
        assertThat(subOrderRepository.findById(subOrderId).get().status).isEqualTo(SubOrderStatus.RETURNING)
    }
}
