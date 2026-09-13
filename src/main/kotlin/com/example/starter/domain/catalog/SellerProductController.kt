package com.example.starter.domain.catalog

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.catalog.dto.AdjustStockRequest
import com.example.starter.domain.catalog.dto.CreateProductRequest
import com.example.starter.domain.catalog.dto.SellerProductResponse
import com.example.starter.domain.catalog.dto.UpdateProductRequest
import com.example.starter.security.userdetails.CustomUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 판매자 상품 관리 API (`ROLE_SELLER`). 본인 상점 상품/옵션/재고 등록·수정.
 */
@RestController
@RequestMapping("/api/seller/products")
class SellerProductController(
    private val sellerProductService: SellerProductService,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal principal: CustomUserDetails): ApiResponse<List<SellerProductResponse>> =
        ApiResponse.success(sellerProductService.getMyProducts(principal.userId))

    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @RequestBody @Valid request: CreateProductRequest,
    ): ApiResponse<SellerProductResponse> =
        ApiResponse.success(sellerProductService.create(principal.userId, request), "상품을 등록했습니다.")

    @PutMapping("/{productId}")
    fun update(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable productId: Long,
        @RequestBody @Valid request: UpdateProductRequest,
    ): ApiResponse<SellerProductResponse> =
        ApiResponse.success(sellerProductService.update(principal.userId, productId, request), "상품을 수정했습니다.")

    @PatchMapping("/{productId}/stock")
    fun adjustStock(
        @AuthenticationPrincipal principal: CustomUserDetails,
        @PathVariable productId: Long,
        @RequestBody @Valid request: AdjustStockRequest,
    ): ApiResponse<SellerProductResponse> =
        ApiResponse.success(
            sellerProductService.adjustStock(principal.userId, productId, request.optionId!!, request.quantity!!),
            "재고를 조정했습니다.",
        )
}
