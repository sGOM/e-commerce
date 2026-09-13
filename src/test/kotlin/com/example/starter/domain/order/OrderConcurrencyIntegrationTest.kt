package com.example.starter.domain.order

import com.example.starter.domain.cart.repository.CartRepository
import com.example.starter.domain.catalog.entity.Inventory
import com.example.starter.domain.catalog.entity.Product
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.InventoryRepository
import com.example.starter.domain.catalog.repository.ProductRepository
import com.example.starter.domain.order.dto.CreateOrderRequest
import com.example.starter.domain.order.dto.ShippingAddressRequest
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.repository.UserRepository
import com.example.starter.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 재고 동시성 — PRD 최우선 요구사항: 동시 주문에도 **초과 판매 0건**.
 *
 * 여러 회원이 같은 옵션을 동시에 주문할 때, 원자적 UPDATE 예약이 정확히 재고 수량만큼만 성공해야 한다.
 * 트랜잭션이 스레드 경계를 넘지 못하므로 이 클래스는 비-Transactional 이며, 생성 데이터는 직접 정리한다.
 */
class OrderConcurrencyIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var orderService: OrderService
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sellerRepository: SellerRepository
    @Autowired lateinit var productRepository: ProductRepository
    @Autowired lateinit var inventoryRepository: InventoryRepository
    @Autowired lateinit var cartRepository: CartRepository
    @Autowired lateinit var orderRepository: OrderRepository

    @AfterEach
    fun cleanup() {
        orderRepository.deleteAll()
        cartRepository.deleteAll()
        productRepository.deleteAll() // options/inventories cascade
        sellerRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `같은 옵션을 동시에 주문해도 재고를 초과 판매하지 않는다`() {
        val stock = 5
        val contenders = 20

        // 판매 상품(재고 5) + 각 회원의 장바구니에 1개씩 미리 담아둔다
        val seller = sellerRepository.save(seller("conc"))
        val product = Product(seller = seller, name = "동시성-상품", basePrice = 10_000, status = ProductStatus.ON_SALE)
        val option = ProductOption(name = "기본", sku = "CONC-SKU", additionalPrice = 0)
        option.assignInventory(Inventory(quantity = stock, reserved = 0))
        product.addOption(option)
        val optionId = productRepository.save(product).options.first().id!!

        val userIds = (1..contenders).map { i ->
            val user = userRepository.save(User(email = "conc-$i@example.com", password = "{noop}x", name = "conc-$i"))
            // 장바구니 담기는 서비스가 아닌 직접 호출 대신 cart 도메인을 거치지 않고 주문 전용으로 준비
            cartRepository.save(com.example.starter.domain.cart.entity.Cart(userId = user.id!!).apply {
                addOrIncrease(option, 1)
            })
            user.id!!
        }

        val req = CreateOrderRequest(
            ordererName = "홍길동",
            ordererPhone = "010-0000-0000",
            ordererEmail = "buyer@example.com",
            shippingAddress = ShippingAddressRequest("홍길동", "010-0000-0000", "12345", "서울 1", null),
        )
        val success = AtomicInteger(0)
        val conflict = AtomicInteger(0)
        val unexpected = ConcurrentLinkedQueue<String>()

        val pool = Executors.newFixedThreadPool(contenders)
        val ready = CountDownLatch(contenders)
        val go = CountDownLatch(1)
        userIds.forEach { uid ->
            pool.submit {
                ready.countDown()
                go.await()
                try {
                    orderService.createFromCart(uid, req)
                    success.incrementAndGet()
                } catch (e: com.example.starter.common.exception.BusinessException) {
                    if (e.errorCode == com.example.starter.common.exception.ErrorCode.INSUFFICIENT_STOCK) {
                        conflict.incrementAndGet()
                    } else {
                        unexpected.add(e.errorCode.code)
                    }
                } catch (e: Exception) {
                    unexpected.add(e.javaClass.simpleName + ":" + e.message)
                }
            }
        }
        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        assertEquals(emptyList<String>(), unexpected.toList(), "예상치 못한 예외 없음")
        assertEquals(stock, success.get(), "성공한 주문 수 == 재고 수")
        assertEquals(contenders - stock, conflict.get(), "나머지는 재고 부족")

        val finalReserved = inventoryRepository.findById(
            inventoryRepository.findAll().first { it.option.id == optionId }.id!!,
        ).get().reserved
        assertEquals(stock, finalReserved, "예약 재고는 정확히 재고 수량 — 초과 판매 0건")
    }

    private fun seller(name: String): Seller {
        val user = userRepository.save(User(email = "$name-seller@example.com", password = "{noop}x", name = name))
        return Seller(userId = user.id!!, storeName = name, status = SellerStatus.ACTIVE)
    }
}
