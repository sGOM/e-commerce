package com.example.starter.domain.flashsale.dto

import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.flashsale.entity.FlashSale
import com.example.starter.domain.flashsale.entity.FlashSalePhase
import com.example.starter.domain.flashsale.entity.FlashSaleStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant

/**
 * 타임딜 등록 요청(셀러/관리자 공용). 서버가 대상 옵션의 정가(기본가+옵션추가금)를 조회해
 * [originalPrice] 를 직접 스냅샷하므로, 클라이언트는 정가를 보내지 않는다(서버 금액 계산 원칙).
 * 판매자 소유권 검증은 호출측(셀러/관리자)에 따라 서비스 계층에서 다르게 적용한다.
 */
data class CreateFlashSaleRequest(
    @field:NotNull
    val productOptionId: Long?,
    @field:NotNull @field:Positive
    val salePrice: Long?,
    @field:NotNull
    val startAt: Instant?,
    @field:NotNull
    val endAt: Instant?,
    @field:NotNull @field:Positive
    val limitQuantity: Int?,
)

/** 관리자 타임딜 검색 조건. */
data class AdminFlashSaleSearchCondition(
    val status: FlashSaleStatus? = null,
    val sellerId: Long? = null,
)

/** 타임딜 응답 — 진행 단계([phase])는 조회 시점 기준 파생 계산 값이다. */
data class FlashSaleResponse(
    val id: Long,
    val productOptionId: Long,
    val productId: Long,
    val productName: String,
    val optionName: String,
    val sellerId: Long,
    val storeName: String,
    val originalPrice: Long,
    val salePrice: Long,
    val limitQuantity: Int,
    val soldQuantity: Int,
    val remainingQuantity: Int,
    val startAt: Instant,
    val endAt: Instant,
    val phase: FlashSalePhase,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(flashSale: FlashSale, option: ProductOption, now: Instant = Instant.now()) = FlashSaleResponse(
            id = requireNotNull(flashSale.id),
            productOptionId = flashSale.productOptionId,
            productId = requireNotNull(option.product.id),
            productName = option.product.name,
            optionName = option.name,
            sellerId = flashSale.sellerId,
            storeName = option.product.seller.storeName,
            originalPrice = flashSale.originalPrice,
            salePrice = flashSale.salePrice,
            limitQuantity = flashSale.limitQuantity,
            soldQuantity = flashSale.soldQuantity,
            remainingQuantity = (flashSale.limitQuantity - flashSale.soldQuantity).coerceAtLeast(0),
            startAt = flashSale.startAt,
            endAt = flashSale.endAt,
            phase = flashSale.phaseAt(now),
            createdAt = flashSale.createdAt,
            updatedAt = flashSale.updatedAt,
        )
    }
}
