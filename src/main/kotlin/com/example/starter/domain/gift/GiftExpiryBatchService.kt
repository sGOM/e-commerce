package com.example.starter.domain.gift

import com.example.starter.domain.gift.dto.GiftExpiryBatchResult
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.order.OrderService
import com.example.starter.domain.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 선물 미수락 만료 배치(AC9/AC10). [GiftExpiryScheduler] 또는 관리자 수동 트리거가 호출한다.
 * 구독 배치([com.example.starter.domain.subscription.DeliverySubscriptionBillingService])와 동일하게
 * 대상 1건의 실패가 다른 건 처리를 막지 않도록 개별로 격리한다.
 */
@Service
@Transactional(readOnly = true)
class GiftExpiryBatchService(
    private val giftClaimRepository: GiftClaimRepository,
    private val orderRepository: OrderRepository,
    private val orderService: OrderService,
    private val notificationService: NotificationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun expireDueClaims(now: Instant = Instant.now()): GiftExpiryBatchResult {
        val dueClaims = giftClaimRepository.findByStatusAndExpiresAtBefore(GiftClaimStatus.PENDING, now)
        var expired = 0
        var errored = 0
        dueClaims.forEach { claim ->
            try {
                processExpiry(claim.id!!, now)
                expired++
            } catch (ex: Exception) {
                log.error("선물 만료 배치 처리 실패(giftClaimId={}) — 이 건만 격리하고 다음 건으로 계속 진행", claim.id, ex)
                errored++
            }
        }
        log.info("선물 만료 배치 완료: 만료취소 {}건 / 오류 {}건", expired, errored)
        return GiftExpiryBatchResult(expiredCount = expired, erroredCount = errored)
    }

    /** 개별 건 처리(별도 트랜잭션 단위) — 링크 만료 처리 + 주문 취소/환불(AC9) + 구매자 안내(AC10). */
    @Transactional
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
