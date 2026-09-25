package com.example.starter.domain.subscription

import com.example.starter.domain.billing.gateway.BillingKeyGateway
import com.example.starter.domain.billing.gateway.ChargeBillingKeyCommand
import com.example.starter.domain.catalog.entity.ProductStatus
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.order.OrderService
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.payment.entity.Payment
import com.example.starter.domain.payment.repository.PaymentRepository
import com.example.starter.domain.point.PointService
import com.example.starter.domain.subscription.entity.DeliverySubscription
import com.example.starter.domain.subscription.entity.DeliverySubscriptionHistory
import com.example.starter.domain.subscription.entity.DeliverySubscriptionHistoryResult
import com.example.starter.domain.subscription.entity.DeliverySubscriptionPolicy
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import com.example.starter.domain.subscription.repository.DeliverySubscriptionBillingKeyRepository
import com.example.starter.domain.subscription.repository.DeliverySubscriptionHistoryRepository
import com.example.starter.domain.subscription.repository.DeliverySubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 정기배송 회차 1건 처리(핵심 로직, AC6~AC9). 두 호출 경로가 이 로직을 공유한다:
 * - [processDueCycle] — 배치([DeliverySubscriptionBillingService])가 여러 건을 순회하며 호출.
 *   구독 1건 = 트랜잭션 1개(`REQUIRES_NEW`)로 격리해, 한 건의 실패(재고부족/결제거절/예외)가 다른
 *   구독 처리에 영향을 주지 않는다("개별 실패 격리" — 기획서 §3 AC7/AC8).
 * - [processImmediateFirstCycle] — 등록 즉시 시작(AC2)일 때 [DeliverySubscriptionService.register] 가
 *   같은 트랜잭션에 합류시켜(`REQUIRED`) 호출한다. 이 경로는 항목이 단 1건뿐이라 격리가 필요 없고,
 *   오히려 방금 저장한(아직 커밋 전) 구독 행을 그대로 재사용해야 하므로 `REQUIRES_NEW` 로 새 트랜잭션을
 *   열면 안 된다 — 새 트랜잭션은 격리 수준(READ COMMITTED)상 아직 커밋되지 않은 구독 행을 볼 수 없다.
 *   참고: PostgreSQL 트랜잭션 격리 — https://www.postgresql.org/docs/current/transaction-iso.html
 *
 * 재고/상품상태 확인은 예외를 던지지 않는 "사전 점검"으로 최대한 처리하고(품절/판매중지는 흔한 경우),
 * [com.example.starter.domain.order.OrderService.createSubscriptionOrder] 호출 이후에만 발생하는
 * 재고 경쟁(레이스) 실패는 예외로 전파시켜 트랜잭션 전체를 롤백한다 — `nextOrderAt` 이 전진하지 않으므로
 * 다음 배치 실행 때 자동으로 재시도된다(별도 재시도 큐 불필요, self-healing).
 */
@Service
@Transactional(readOnly = true)
class DeliverySubscriptionCycleService(
    private val subscriptionRepository: DeliverySubscriptionRepository,
    private val historyRepository: DeliverySubscriptionHistoryRepository,
    private val billingKeyRepository: DeliverySubscriptionBillingKeyRepository,
    private val billingKeyGateway: BillingKeyGateway,
    private val productOptionRepository: ProductOptionRepository,
    private val orderService: OrderService,
    private val paymentRepository: PaymentRepository,
    private val pointService: PointService,
    private val notificationService: NotificationService,
) {

    /** 배치 전용 — 구독 단위 격리 트랜잭션. 이미 처리됐거나 대상이 아니게 된 경우(레이스) null. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processDueCycle(subscriptionId: Long, now: Instant, policy: DeliverySubscriptionPolicy): DeliverySubscriptionHistoryResult? {
        val subscription = subscriptionRepository.findById(subscriptionId).orElse(null) ?: return null
        if (subscription.status != DeliverySubscriptionStatus.ACTIVE || subscription.nextOrderAt.isAfter(now)) {
            return null
        }
        return processCycle(subscription, now, policy)
    }

    /** 등록 즉시 시작(AC2) 전용 — 호출자의 트랜잭션에 합류한다(REQUIRED, 기본값). */
    @Transactional
    fun processImmediateFirstCycle(
        subscription: DeliverySubscription,
        now: Instant,
        policy: DeliverySubscriptionPolicy,
    ): DeliverySubscriptionHistoryResult = processCycle(subscription, now, policy)

    private fun processCycle(
        subscription: DeliverySubscription,
        now: Instant,
        policy: DeliverySubscriptionPolicy,
    ): DeliverySubscriptionHistoryResult {
        if (subscription.skipRequested) {
            subscription.consumeUserSkip()
            recordHistory(subscription, now, DeliverySubscriptionHistoryResult.SKIPPED_BY_USER, null, "회원 요청으로 이번 회차를 건너뛰었습니다.")
            return DeliverySubscriptionHistoryResult.SKIPPED_BY_USER
        }

        val option = productOptionRepository.findWithProductAndInventoryById(subscription.optionId).orElse(null)
        if (option == null) {
            subscription.autoPause()
            recordHistory(subscription, now, DeliverySubscriptionHistoryResult.PAUSED_PRODUCT_UNAVAILABLE, null, "상품 옵션을 찾을 수 없음")
            notify(subscription, "정기배송이 일시정지되었습니다.", "상품 정보를 찾을 수 없어 정기배송이 자동으로 일시정지되었습니다.")
            return DeliverySubscriptionHistoryResult.PAUSED_PRODUCT_UNAVAILABLE
        }
        val product = option.product
        if (!product.status.isPurchasable) {
            // 일시 품절(SOLD_OUT)은 스킵만, 완전 판매중지(HIDDEN/DRAFT)는 자동 일시정지(§4).
            return if (product.status == ProductStatus.SOLD_OUT) {
                subscription.recordSkippedOutOfStock()
                recordHistory(subscription, now, DeliverySubscriptionHistoryResult.SKIPPED_OUT_OF_STOCK, null, "일시 품절")
                notify(
                    subscription,
                    "정기배송 회차가 건너뛰어졌습니다.",
                    "'${product.name}' 상품이 일시 품절이라 이번 회차를 건너뛰었습니다. 다음 회차에 다시 시도합니다.",
                )
                DeliverySubscriptionHistoryResult.SKIPPED_OUT_OF_STOCK
            } else {
                subscription.autoPause()
                recordHistory(subscription, now, DeliverySubscriptionHistoryResult.PAUSED_PRODUCT_UNAVAILABLE, null, "상품 판매중지(${product.status})")
                notify(
                    subscription,
                    "정기배송이 일시정지되었습니다.",
                    "'${product.name}' 상품 판매가 중단되어 정기배송이 자동으로 일시정지되었습니다.",
                )
                DeliverySubscriptionHistoryResult.PAUSED_PRODUCT_UNAVAILABLE
            }
        }

        // 재고 원자적 예약 + 주문 조립(가격은 지금 이 순간 현재가 스냅샷, AC9). 이 시점 이후의 재고
        // 부족(레이스)은 예외로 전파해 이 트랜잭션 전체를 롤백시킨다(클래스 docs 참고).
        val order = orderService.createSubscriptionOrder(
            userId = subscription.userId,
            optionId = subscription.optionId,
            quantity = subscription.quantity,
            ordererName = subscription.ordererName,
            ordererPhone = subscription.ordererPhone,
            ordererEmail = subscription.ordererEmail,
            shippingAddress = subscription.shippingAddress,
        )
        val orderId = requireNotNull(order.id)

        val billingKey = billingKeyRepository.findByUserId(subscription.userId)
        val chargeResult = billingKey?.let {
            billingKeyGateway.chargeBillingKey(
                ChargeBillingKeyCommand(it.gatewayBillingKey, order.payableAmount, "DSUB-${subscription.id}-${subscription.nextOrderAt.epochSecond}"),
            )
        }

        if (billingKey == null || chargeResult?.success != true) {
            orderService.cancelOrder(subscription.userId, orderId) // 재고 예약 원복(기존 취소 흐름 재사용)
            subscription.recordPaymentFailure()
            val reason = chargeResult?.message ?: "등록된 결제수단 없음"
            recordHistory(subscription, now, DeliverySubscriptionHistoryResult.PAYMENT_FAILED, orderId, reason)
            notify(
                subscription,
                "정기배송 결제에 실패했습니다.",
                "'${product.name}' 정기결제가 실패했습니다($reason). 이번 회차는 건너뛰고 다음 회차에 다시 시도합니다.",
            )
            if (subscription.consecutiveFailureCount >= policy.maxConsecutiveFailures) {
                subscription.autoPause()
                notify(
                    subscription,
                    "정기배송이 일시정지되었습니다.",
                    "정기결제가 ${policy.maxConsecutiveFailures}회 연속 실패해 정기배송이 자동으로 일시정지되었습니다. 카드를 다시 등록해 주세요.",
                )
            }
            return DeliverySubscriptionHistoryResult.PAYMENT_FAILED
        }

        val payment = Payment(orderId = orderId, amount = order.payableAmount, method = "BILLING")
        payment.markPaid(chargeResult.transactionId)
        paymentRepository.save(payment)
        order.status = OrderStatus.PAID
        order.subOrders.forEach { it.status = SubOrderStatus.PAID }
        pointService.earn(subscription.userId, order.merchandisePayable, orderId) // 일반 결제와 동일 포인트 적립 정책

        subscription.recordOrderCreated()
        recordHistory(subscription, now, DeliverySubscriptionHistoryResult.ORDER_CREATED, orderId, null)
        return DeliverySubscriptionHistoryResult.ORDER_CREATED
    }

    private fun recordHistory(
        subscription: DeliverySubscription,
        now: Instant,
        result: DeliverySubscriptionHistoryResult,
        orderId: Long?,
        detail: String?,
    ) {
        historyRepository.save(
            DeliverySubscriptionHistory(
                subscriptionId = requireNotNull(subscription.id),
                attemptedAt = now,
                result = result,
                orderId = orderId,
                detail = detail,
            ),
        )
    }

    private fun notify(subscription: DeliverySubscription, title: String, body: String) {
        notificationService.notify(subscription.userId, NotificationType.DELIVERY_SUBSCRIPTION, title, body)
    }
}
