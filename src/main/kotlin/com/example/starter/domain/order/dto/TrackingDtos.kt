package com.example.starter.domain.order.dto

import com.example.starter.domain.order.tracking.TrackingEvent

/** 배송 조회 응답. [supported] 가 false 면 택배사 조회를 지원하지 않아 송장번호만 보여준다. */
data class TrackingResponse(
    val courier: String,
    val trackingNumber: String,
    val supported: Boolean,
    val delivered: Boolean,
    val events: List<TrackingEvent>,
)
