package com.example.starter.domain.order.entity

/** 배송 상태. 송장 등록 시 SHIPPED, 수령 확인 시 DELIVERED. */
enum class ShipmentStatus {
    SHIPPED,
    DELIVERED,
}
