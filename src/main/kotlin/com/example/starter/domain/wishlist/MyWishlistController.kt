package com.example.starter.domain.wishlist

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.wishlist.dto.AddWishlistRequest
import com.example.starter.domain.wishlist.dto.WishlistResponse
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 마이페이지 위시리스트(찜) API (인증 필요, 게스트 미지원 — `docs/planning/wishlist-price-alert.md` §4).
 */
@RestController
@RequestMapping("/api/me/wishlist")
class MyWishlistController(
    private val wishlistService: WishlistService,
) {

    @GetMapping
    fun myWishlist(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestParam(defaultValue = "false") priceDropOnly: Boolean,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<WishlistResponse>> =
        ApiResponse.success(wishlistService.getMyWishlist(principal.userId, priceDropOnly, pageable))

    @PostMapping
    fun add(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: AddWishlistRequest,
    ): ApiResponse<WishlistResponse> =
        ApiResponse.success(wishlistService.add(principal.userId, request.productId!!), "위시리스트에 담았습니다.")

    @DeleteMapping("/{productId}")
    fun remove(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable productId: Long,
    ): ApiResponse<Unit> {
        wishlistService.remove(principal.userId, productId)
        return ApiResponse.success("위시리스트에서 삭제했습니다.")
    }
}
