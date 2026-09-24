package com.example.starter.domain.promotion

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.promotion.dto.AdminCollectionSearchCondition
import com.example.starter.domain.promotion.dto.ChangeCollectionStatusRequest
import com.example.starter.domain.promotion.dto.CollectionDetailResponse
import com.example.starter.domain.promotion.dto.CollectionRequest
import com.example.starter.domain.promotion.dto.CollectionSummaryResponse
import com.example.starter.domain.promotion.dto.ReplaceCollectionProductsRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자(MD) 컬렉션 편성 API (`ROLE_ADMIN`). 생성/메타 수정/상태 전이/상품 편성을 다룬다.
 * 이번 범위는 관리자 전용이다(셀러 신청 플로우는 Out of scope, 기획서 §8).
 */
@RestController
@RequestMapping("/api/admin/collections")
@PreAuthorize("hasRole('ADMIN')")
class AdminCollectionController(
    private val adminCollectionService: AdminCollectionService,
) {

    @GetMapping
    fun search(
        condition: AdminCollectionSearchCondition,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<PageResponse<CollectionSummaryResponse>> =
        ApiResponse.success(adminCollectionService.search(condition, pageable))

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<CollectionDetailResponse> =
        ApiResponse.success(adminCollectionService.getDetail(id))

    @PostMapping
    fun create(@RequestBody @Valid request: CollectionRequest): ApiResponse<CollectionDetailResponse> =
        ApiResponse.success(adminCollectionService.create(request), "컬렉션을 등록했습니다.")

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: CollectionRequest,
    ): ApiResponse<CollectionDetailResponse> =
        ApiResponse.success(adminCollectionService.update(id, request), "컬렉션을 수정했습니다.")

    @PatchMapping("/{id}/status")
    fun changeStatus(
        @PathVariable id: Long,
        @RequestBody @Valid request: ChangeCollectionStatusRequest,
    ): ApiResponse<CollectionDetailResponse> =
        ApiResponse.success(adminCollectionService.changeStatus(id, request), "컬렉션 상태를 변경했습니다.")

    @PutMapping("/{id}/products")
    fun replaceProducts(
        @PathVariable id: Long,
        @RequestBody @Valid request: ReplaceCollectionProductsRequest,
    ): ApiResponse<CollectionDetailResponse> =
        ApiResponse.success(adminCollectionService.replaceProducts(id, request), "편성 상품을 저장했습니다.")
}
