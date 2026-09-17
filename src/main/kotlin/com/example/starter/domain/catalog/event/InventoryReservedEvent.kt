package com.example.starter.domain.catalog.event

/** 주문 생성으로 옵션 재고가 [quantity] 만큼 예약됐을 때 발행된다(저재고 알림 트리거, ROADMAP 4.3). */
data class InventoryReservedEvent(val optionId: Long, val quantity: Int)
