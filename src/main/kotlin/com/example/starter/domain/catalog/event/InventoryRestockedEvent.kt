package com.example.starter.domain.catalog.event

/**
 * 옵션의 가용재고(quantity - reserved)가 0 → 1 이상으로 전이될 때 발행되는 이벤트.
 *
 * `catalog` 도메인은 재고 원자적 갱신에만 집중하고, 이 이벤트를 누가/왜 구독하는지는 몰라도 된다
 * (현재는 `restock` 도메인이 구독해 재입고 알림을 트리거한다, `docs/planning/restock-alert.md`).
 * 발행은 재고 변경 트랜잭션 커밋 이후 처리되도록 [org.springframework.transaction.event.TransactionalEventListener]
 * 로 구독하는 것을 전제로 한다 — 재고 갱신 자체를 알림 처리로 지연시키지 않기 위함.
 */
data class InventoryRestockedEvent(val optionId: Long)
