package com.example.starter.domain.catalog.event

/**
 * 상품 정가([com.example.starter.domain.catalog.entity.Product.basePrice])가 변경될 때 발행되는 이벤트.
 *
 * `catalog` 도메인은 가격 변경 사실만 알리고, 누가/왜 구독하는지는 몰라도 된다(`InventoryRestockedEvent`
 * 와 동일 설계 원칙 — 현재는 `wishlist` 도메인이 구독해 하락 시에만 가격 인하 알림을 트리거한다,
 * `docs/planning/wishlist-price-alert.md`). 옵션 추가금(`ProductOption.additionalPrice`)이나
 * 타임딜/쿠폰 적용가 변경은 이 이벤트 대상이 아니다(정가만).
 *
 * 발행측은 가격이 오르든 내리든 항상 이벤트를 발행하고, "인하인지" 판정은 구독측(리스너)이
 * [oldPrice]/[newPrice] 비교로 수행한다 — `catalog` 는 가격 인하 정책을 몰라도 되게 하기 위함.
 */
data class ProductPriceChangedEvent(val productId: Long, val oldPrice: Long, val newPrice: Long)
