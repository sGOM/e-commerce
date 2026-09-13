package com.example.starter.domain.wishlist

import com.example.starter.domain.catalog.event.ProductPriceChangedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 상품 가격 변경 이벤트 구독. 가격 변경 트랜잭션이 **커밋된 이후**([TransactionPhase.AFTER_COMMIT])에만
 * 실행되며, 전용 스레드 풀에서 비동기로 처리한다([com.example.starter.domain.restock.RestockAlertEventListener]
 * 와 동일 패턴, `docs/planning/wishlist-price-alert.md` AC12).
 *
 * 가격이 오르거나 동일하면 알림 대상이 아니다(AC9) — 판정은 [WishlistService.notifyPriceDrop] 안에서
 * 한 번 더 최신 가격 기준으로 재확인한다.
 */
@Component
class WishlistPriceAlertEventListener(
    private val wishlistService: WishlistService,
) {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProductPriceChanged(event: ProductPriceChangedEvent) {
        if (event.newPrice < event.oldPrice) {
            wishlistService.notifyPriceDrop(event.productId, event.newPrice)
        }
    }
}
