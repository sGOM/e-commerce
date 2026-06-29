package com.example.starter.domain.cart

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.cart.dto.AddCartItemRequest
import com.example.starter.domain.cart.dto.CartResponse
import com.example.starter.domain.cart.dto.GuestCartRequest
import com.example.starter.domain.cart.dto.UpdateCartItemRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 장바구니 API (인증 필요). 모든 작업은 인증된 본인 장바구니에만 적용된다.
 */
@RestController
@RequestMapping("/api/cart")
class CartController(
    private val cartService: CartService,
) {

    @GetMapping
    fun getCart(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<CartResponse> =
        ApiResponse.success(cartService.getCart(principal.userId))

    /**
     * 게스트(비회원) 장바구니 계산/검증. 클라이언트가 localStorage에 보관한 항목을 그대로 전달하면
     * 현재가·재고·구매가능 여부를 계산해 돌려준다. 서버에 저장하지 않으며 인증도 필요 없다.
     */
    @PostMapping("/guest")
    fun previewGuestCart(@RequestBody @Valid request: GuestCartRequest): ApiResponse<CartResponse> =
        ApiResponse.success(cartService.previewGuestCart(request.items))

    @PostMapping("/items")
    fun addItem(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: AddCartItemRequest,
    ): ApiResponse<CartResponse> =
        ApiResponse.success(
            cartService.addItem(principal.userId, request.optionId!!, request.quantity!!),
            "장바구니에 담았습니다.",
        )

    @PatchMapping("/items/{itemId}")
    fun updateItem(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable itemId: Long,
        @RequestBody @Valid request: UpdateCartItemRequest,
    ): ApiResponse<CartResponse> =
        ApiResponse.success(
            cartService.updateItemQuantity(principal.userId, itemId, request.quantity!!),
            "수량을 변경했습니다.",
        )

    @DeleteMapping("/items/{itemId}")
    fun removeItem(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable itemId: Long,
    ): ApiResponse<CartResponse> =
        ApiResponse.success(cartService.removeItem(principal.userId, itemId), "항목을 삭제했습니다.")
}
