package com.example.starter.domain.point.entity

/** 적립 lot 상태. */
enum class PointLotStatus {
    ACTIVE, // 잔여가 남아 사용 가능
    EXHAUSTED, // 모두 사용됨
    EXPIRED, // 만료/회수로 소멸
}
