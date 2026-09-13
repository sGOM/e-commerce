package com.example.starter.domain.cart

import com.example.starter.domain.cart.dto.CartReminderBatchResult
import com.example.starter.domain.cart.repository.CartRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * 장바구니 이탈 리마인드 배치. [CartReminderScheduler] 또는 관리자 수동 트리거가 호출한다.
 * MVP는 인앱 알림만 발송하고 자동 쿠폰은 발급하지 않는다(어뷰징 회피).
 *
 * [com.example.starter.domain.gift.GiftExpiryBatchService] 와 동일하게 대상 1건의 실패가 다른 건
 * 처리를 막지 않도록 개별로 격리한다.
 */
@Service
@Transactional(readOnly = true)
class CartReminderBatchService(
    private val cartRepository: CartRepository,
    private val notificationService: NotificationService,
    private val cartReminderProperties: CartReminderProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sendReminders(now: Instant = Instant.now()): CartReminderBatchResult {
        val threshold = now.minus(Duration.ofHours(cartReminderProperties.inactivityHours))
        val targets = cartRepository.findAbandonedCarts(threshold)
        var reminded = 0
        var errored = 0
        targets.forEach { cart ->
            try {
                processReminder(cart.id!!, threshold, now)
                reminded++
            } catch (ex: Exception) {
                log.error("장바구니 리마인드 처리 실패(cartId={}) — 이 건만 격리하고 다음 건으로 계속 진행", cart.id, ex)
                errored++
            }
        }
        val result = CartReminderBatchResult(reminded, errored)
        log.info("장바구니 이탈 리마인드 배치 완료: {}", result)
        return result
    }

    /**
     * 개별 건 처리(별도 트랜잭션 단위). 스캔과 처리 사이(잠깐의 지연) 그 사이 결제/추가활동이
     * 있었을 수 있어 조건을 다시 한번 확인한다(레이스 대응 — 재입고/선물만료 배치와 동일 원칙).
     */
    @Transactional
    fun processReminder(cartId: Long, threshold: Instant, now: Instant) {
        val cart = cartRepository.findById(cartId).orElse(null) ?: return
        if (cart.items.isEmpty()) return
        if (!cart.lastActivityAt.isBefore(threshold)) return // 그 사이 활동이 있었음
        val lastReminderAt = cart.lastReminderAt
        if (lastReminderAt != null && !lastReminderAt.isBefore(cart.lastActivityAt)) return // 이미 이번 이탈 구간 발송 완료

        cart.markReminded(now)
        notificationService.notify(
            userId = cart.userId,
            type = NotificationType.CART_REMINDER,
            title = "장바구니에 담아두신 상품이 기다리고 있어요",
            body = "장바구니에 담아두신 상품이 아직 결제되지 않았습니다. 지금 확인해보세요.",
            linkUrl = "/cart",
        )
    }
}
