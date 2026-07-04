package com.example.starter.domain.gift.entity

/**
 * 선물 링크(토큰) 상태. 전이: PENDING → CLAIMED(수령자 수락) / EXPIRED(기한 경과 배치 취소) /
 * CANCELED(구매자가 수락 전 취소). 모두 종단 상태이며 되돌아가지 않는다(AC8 — 1회성).
 */
enum class GiftClaimStatus {
    PENDING,
    CLAIMED,
    EXPIRED,
    CANCELED,
}
