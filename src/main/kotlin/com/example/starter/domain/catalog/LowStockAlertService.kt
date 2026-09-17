package com.example.starter.domain.catalog

import com.example.starter.domain.catalog.event.InventoryReservedEvent
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 저재고 알림(ROADMAP 4.3). 주문 예약으로 옵션 가용재고가 임계치(`inventory.low-stock-threshold`, 기본 5)를
 * **넘어서 이하로 내려간 순간에만** 판매자 인앱 알림함에 알린다 — 이미 임계치 이하인 옵션은 주문마다 다시 알리지 않는다.
 * 재입고 알림과 같이 커밋 이후 비동기로 처리해 주문 응답을 늦추지 않는다.
 */
// ponytail: 커밋 후 재고를 다시 읽어 전이를 추정하므로 동시 주문이 겹치면 알림이 빠지거나 한 번 더 갈 수 있다 — 정확히 한 번이 필요하면 옵션별 알림 상태 컬럼으로 전환
@Service
class LowStockAlertService(
    private val productOptionRepository: ProductOptionRepository,
    private val notificationService: NotificationService,
    @Value("\${inventory.low-stock-threshold:5}") private val threshold: Int,
) {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun on(event: InventoryReservedEvent) = onReserved(event.optionId, event.quantity)

    @Transactional
    fun onReserved(optionId: Long, quantity: Int) {
        val option = productOptionRepository.findWithProductAndInventoryById(optionId).orElse(null) ?: return
        val available = option.inventory?.available ?: return
        if (available + quantity <= threshold || available > threshold) return
        val product = option.product
        notificationService.notify(
            userId = product.seller.userId,
            type = NotificationType.LOW_STOCK,
            title = "재고가 얼마 남지 않았어요",
            body = "'${product.name}' ${option.name} 옵션의 판매 가능 재고가 ${available}개 남았습니다.",
            linkUrl = "/seller/products",
        )
    }
}
