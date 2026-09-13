package com.example.starter.domain.cart.dto

/** 장바구니 이탈 리마인드 배치 실행 결과. */
data class CartReminderBatchResult(
    val remindedCount: Int,
    val erroredCount: Int,
)
