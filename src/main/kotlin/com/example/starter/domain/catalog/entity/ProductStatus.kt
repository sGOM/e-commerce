package com.example.starter.domain.catalog.entity

/**
 * 상품 판매 상태.
 */
enum class ProductStatus {
    /** 작성 중 (미노출) */
    DRAFT,

    /** 판매 중 (노출 + 구매 가능) */
    ON_SALE,

    /** 품절 (노출하되 구매 불가) */
    SOLD_OUT,

    /** 숨김 (미노출) */
    HIDDEN,
    ;

    /** 고객에게 노출되는 상태인지 (검색/조회 가능) */
    val isVisible: Boolean
        get() = this == ON_SALE || this == SOLD_OUT

    /** 구매 가능 상태인지 */
    val isPurchasable: Boolean
        get() = this == ON_SALE

    companion object {
        /** 고객 노출 대상 상태 목록 (검색 필터용) */
        val VISIBLE = listOf(ON_SALE, SOLD_OUT)
    }
}
