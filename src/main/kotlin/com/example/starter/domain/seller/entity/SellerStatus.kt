package com.example.starter.domain.seller.entity

/**
 * 입점 판매자(Store) 상태.
 */
enum class SellerStatus {
    /** 입점 신청 후 관리자 심사 대기 */
    PENDING,

    /** 승인되어 영업 중 (상품 판매 가능) */
    ACTIVE,

    /** 관리자에 의해 영업 정지 */
    SUSPENDED,
    ;

    /** 상품 판매(노출) 가능 여부 */
    val canSell: Boolean
        get() = this == ACTIVE
}
