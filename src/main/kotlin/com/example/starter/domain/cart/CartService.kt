package com.example.starter.domain.cart

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.cart.dto.CartItemResponse
import com.example.starter.domain.cart.dto.CartResponse
import com.example.starter.domain.cart.dto.GuestCartItemRequest
import com.example.starter.domain.cart.entity.Cart
import com.example.starter.domain.cart.entity.CartItem
import com.example.starter.domain.cart.repository.CartRepository
import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 회원 장바구니. 담는 시점에 판매상태/재고를 검증한다(결제 직전 재검증은 주문 단계 — Phase 3).
 * 가격은 보관하지 않고 항상 조회 시점 현재가를 사용한다.
 */
@Service
@Transactional(readOnly = true)
class CartService(
    private val cartRepository: CartRepository,
    private val productOptionRepository: ProductOptionRepository,
) {

    fun getCart(userId: Long): CartResponse =
        cartRepository.findWithItemsByUserId(userId)
            .map { CartResponse.from(it) }
            .orElseGet { CartResponse(items = emptyList(), totalQuantity = 0, totalPrice = 0) }

    /**
     * 게스트(localStorage) 장바구니 미리보기. 서버에 저장하지 않고 현재가/재고/구매가능 여부만 계산한다.
     * 클라이언트가 보낸 항목 중 같은 옵션은 수량을 합산하고, 더 이상 존재하지 않는 옵션은 조용히 제외한다.
     */
    fun previewGuestCart(items: List<GuestCartItemRequest>): CartResponse {
        val mergedQuantities = LinkedHashMap<Long, Int>()
        for (item in items) {
            val optionId = item.optionId!!
            mergedQuantities[optionId] = (mergedQuantities[optionId] ?: 0) + item.quantity!!
        }
        val responses = mergedQuantities.entries.mapNotNull { (optionId, quantity) ->
            productOptionRepository.findWithProductAndInventoryById(optionId)
                .map { CartItemResponse.ofGuest(it, quantity) }
                .orElse(null)
        }
        return CartResponse.of(responses)
    }

    @Transactional
    fun addItem(userId: Long, optionId: Long, quantity: Int): CartResponse {
        val cart = getOrCreateCart(userId)
        val option = findOption(optionId)
        val alreadyInCart = cart.items.firstOrNull { it.option.id == optionId }?.quantity ?: 0
        validatePurchasable(option, alreadyInCart + quantity)
        cart.addOrIncrease(option, quantity)
        cartRepository.flush() // 신규 항목의 id 확정 후 응답 생성
        return CartResponse.from(cart)
    }

    @Transactional
    fun updateItemQuantity(userId: Long, itemId: Long, quantity: Int): CartResponse {
        val cart = getOrCreateCart(userId)
        val item = findItem(cart, itemId)
        validatePurchasable(item.option, quantity)
        item.quantity = quantity
        return CartResponse.from(cart)
    }

    @Transactional
    fun removeItem(userId: Long, itemId: Long): CartResponse {
        val cart = getOrCreateCart(userId)
        val item = findItem(cart, itemId)
        cart.removeItem(item)
        return CartResponse.from(cart)
    }

    private fun getOrCreateCart(userId: Long): Cart =
        cartRepository.findWithItemsByUserId(userId)
            .orElseGet { cartRepository.save(Cart(userId = userId)) }

    private fun findOption(optionId: Long): ProductOption =
        productOptionRepository.findWithProductAndInventoryById(optionId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }

    private fun findItem(cart: Cart, itemId: Long): CartItem =
        cart.items.firstOrNull { it.id == itemId }
            ?: throw BusinessException(ErrorCode.CART_ITEM_NOT_FOUND)

    /** 판매중 + 가용 재고가 요청 수량 이상인지 검증 */
    private fun validatePurchasable(option: ProductOption, requiredQuantity: Int) {
        if (!option.product.status.isPurchasable) {
            throw BusinessException(ErrorCode.PRODUCT_NOT_PURCHASABLE)
        }
        val available = option.inventory?.available ?: 0
        if (available < requiredQuantity) {
            throw BusinessException(
                ErrorCode.INSUFFICIENT_STOCK,
                "재고가 부족합니다. (가용 $available 개)",
            )
        }
    }
}
