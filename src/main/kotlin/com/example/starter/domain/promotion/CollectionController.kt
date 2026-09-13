package com.example.starter.domain.promotion

import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.promotion.dto.CollectionDetailResponse
import com.example.starter.domain.promotion.dto.CollectionSummaryResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 고객용 컬렉션(기획전) 조회 API. 인증 불필요(공개) — 게스트도 탐색 가능.
 */
@RestController
@RequestMapping("/api/collections")
class CollectionController(
    private val collectionService: CollectionService,
) {

    @GetMapping
    fun listActive(): ApiResponse<List<CollectionSummaryResponse>> =
        ApiResponse.success(collectionService.listActive())

    @GetMapping("/{id}")
    fun getDetail(@PathVariable id: Long): ApiResponse<CollectionDetailResponse> =
        ApiResponse.success(collectionService.getDetail(id))
}
