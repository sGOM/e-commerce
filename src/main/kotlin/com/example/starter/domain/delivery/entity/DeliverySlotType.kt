package com.example.starter.domain.delivery.entity

/** 배송 슬롯 유형. DAWN(새벽배송)만 [com.example.starter.domain.catalog.entity.Product.dawnDeliveryEligible]
 * 및 [com.example.starter.domain.delivery.entity.DeliveryRegion] 화이트리스트 검증 대상이다. */
enum class DeliverySlotType {
    DAWN,
    DAYTIME,
}
