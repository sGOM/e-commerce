package com.example.starter.domain.cart.dto

import com.example.starter.domain.cart.entity.Cart
import com.example.starter.domain.cart.entity.CartItem
import com.example.starter.domain.catalog.entity.ProductOption
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

/** 장바구니 담기 요청 */
data class AddCartItemRequest(
    @field:NotNull
    val optionId: Long?,
    @field:NotNull
    @field:Min(1)
    val quantity: Int?,
)

/** 수량 변경 요청 */
data class UpdateCartItemRequest(
    @field:NotNull
    @field:Min(1)
    val quantity: Int?,
)

/** 게스트 장바구니(localStorage) 항목 — 클라이언트가 보유한 한 줄 */
data class GuestCartItemRequest(
    @field:NotNull
    val optionId: Long?,
    @field:NotNull
    @field:Min(1)
    val quantity: Int?,
)

/** 게스트 장바구니 계산/검증 요청 — localStorage 전체를 그대로 전달 */
data class GuestCartRequest(
    @field:Valid
    val items: List<GuestCartItemRequest> = emptyList(),
)

/** 장바구니 항목 응답 */
data class CartItemResponse(
    val itemId: Long?, // 회원 항목은 cart_item id, 게스트(localStorage) 항목은 null
    val optionId: Long,
    val productId: Long,
    val productName: String,
    val optionName: String,
    val unitPrice: Long, // 조회 시점 현재가 (상품 기본가 + 옵션 추가금)
    val quantity: Int,
    val lineTotal: Long,
    val availableStock: Int,
    val purchasable: Boolean, // 상품이 판매중이고 재고가 수량 이상인지
    // 체크아웃 화면이 판매자(SubOrder) 단위로 항목을 묶어 배송 슬롯을 선택할 수 있도록 노출한다
    // (`docs/planning/delivery-slot.md` AC6 — 슬롯은 SubOrder=판매자 단위로 선택).
    val sellerId: Long,
    val storeName: String,
    val dawnDeliveryEligible: Boolean,
) {
    companion object {
        fun from(item: CartItem): CartItemResponse {
            val option = item.option
            val product = option.product
            val unitPrice = product.basePrice + option.additionalPrice
            val stock = option.inventory?.available ?: 0
            return CartItemResponse(
                itemId = requireNotNull(item.id),
                optionId = requireNotNull(option.id),
                productId = requireNotNull(product.id),
                productName = product.name,
                optionName = option.name,
                unitPrice = unitPrice,
                quantity = item.quantity,
                lineTotal = unitPrice * item.quantity,
                availableStock = stock,
                purchasable = product.status.isPurchasable && stock >= item.quantity,
                sellerId = requireNotNull(product.seller.id),
                storeName = product.seller.storeName,
                dawnDeliveryEligible = product.dawnDeliveryEligible,
            )
        }

        /** 게스트(localStorage) 항목 — 서버에 저장하지 않고 현재가/재고만 계산한다. itemId 없음. */
        fun ofGuest(option: ProductOption, quantity: Int): CartItemResponse {
            val product = option.product
            val unitPrice = product.basePrice + option.additionalPrice
            val stock = option.inventory?.available ?: 0
            return CartItemResponse(
                itemId = null,
                optionId = requireNotNull(option.id),
                productId = requireNotNull(product.id),
                productName = product.name,
                optionName = option.name,
                unitPrice = unitPrice,
                quantity = quantity,
                lineTotal = unitPrice * quantity,
                availableStock = stock,
                purchasable = product.status.isPurchasable && stock >= quantity,
                sellerId = requireNotNull(product.seller.id),
                storeName = product.seller.storeName,
                dawnDeliveryEligible = product.dawnDeliveryEligible,
            )
        }
    }
}

/** 장바구니 응답 */
data class CartResponse(
    val items: List<CartItemResponse>,
    val totalQuantity: Int,
    val totalPrice: Long, // 구매 가능 여부와 무관한 현재가 합계
) {
    companion object {
        fun from(cart: Cart): CartResponse =
            of(cart.items.sortedByDescending { it.id }.map { CartItemResponse.from(it) })

        /** 이미 만들어진 항목 목록(회원/게스트 공통)으로 합계를 계산해 응답을 만든다. */
        fun of(items: List<CartItemResponse>): CartResponse =
            CartResponse(
                items = items,
                totalQuantity = items.sumOf { it.quantity },
                totalPrice = items.sumOf { it.lineTotal },
            )
    }
}
