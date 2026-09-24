package com.example.starter.domain.gift

import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.order.OrderService
import com.example.starter.domain.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 선물 만료 1건 처리. [GiftExpiryBatchService] 와 별도 빈이어야 프록시를 거쳐 건별 트랜잭션(REQUIRES_NEW)이 열린다 —
 * 같은 클래스 안의 자기 호출은 `@Transactional` 이 적용되지 않아, 한 건의 실패(예: PG 취소 거절)가 배치 전체
 * 트랜잭션을 rollback-only 로 만들고 이미 환불된 다른 건까지 되돌린다.
 * [com.example.starter.domain.subscription.DeliverySubscriptionCycleService] 와 같은 구성.
 */
@Service
class GiftExpiryProcessor(
    private val giftClaimRepository: GiftClaimRepository,
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val notificationService: NotificationService,
) {

    /** 개별 건 처리(별도 트랜잭션 단위) — 링크 만료 처리 + 주문 취소/환불(AC9) + 구매자 안내(AC10). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processExpiry(claimId: Long, now: Instant) {
        val claim = giftClaimRepository.findById(claimId).orElse(null) ?: return
        if (claim.status != GiftClaimStatus.PENDING || !now.isAfter(claim.expiresAt)) {
            return // 그 사이 수락/취소되었거나 아직 기한이 안 됨(레이스) — 집계하지 않음
        }
        claim.expire()
        orderService.cancelExpiredGiftOrder(claim.orderId)

        orderRepository.findById(claim.orderId).orElse(null)?.userId?.let { userId ->
            notificationService.notify(
                userId = userId,
                type = NotificationType.GIFT,
                title = "선물이 만료되어 취소되었습니다",
                body = "받는 분이 기한 내 배송지를 입력하지 않아 선물 주문이 자동 취소되고 전액 환불되었습니다.",
                linkUrl = "/orders/${claim.orderId}",
            )
        }
    }
}
