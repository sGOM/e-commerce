package com.example.starter.domain.catalog

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.dto.ProductDetailResponse
import com.example.starter.domain.catalog.dto.ProductSearchCondition
import com.example.starter.domain.catalog.dto.ProductSummaryResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 고객용 상품 조회 API. 인증 불필요(공개) — 게스트도 탐색 가능.
 */
@RestController
@RequestMapping("/api/products")
class ProductController(
    private val productService: ProductService,
) {

    @GetMapping
    fun search(
        condition: ProductSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<ProductSummaryResponse>> =
        ApiResponse.success(productService.search(condition, pageable))

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<ProductDetailResponse> =
        ApiResponse.success(productService.getDetail(id))
}
