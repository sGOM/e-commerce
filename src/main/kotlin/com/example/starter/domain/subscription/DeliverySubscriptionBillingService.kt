package com.example.starter.domain.subscription

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.subscription.dto.AdminDeliverySubscriptionResponse
import com.example.starter.domain.subscription.dto.AdminDeliverySubscriptionSearchCondition
import com.example.starter.domain.subscription.dto.DeliverySubscriptionHistoryResponse
import com.example.starter.domain.subscription.entity.DeliverySubscription
import com.example.starter.domain.subscription.entity.DeliverySubscriptionHistoryResult
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import com.example.starter.domain.subscription.repository.DeliverySubscriptionHistoryRepository
import com.example.starter.domain.subscription.repository.DeliverySubscriptionRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 정기배송 배치 오케스트레이터([DeliverySubscriptionBillingScheduler]/관리자 수동 트리거가 호출) +
 * 관리자 조회. 실제 회차 처리는 [DeliverySubscriptionCycleService] 에 위임하며, 이 클래스는 대상
 * 목록을 뽑아 순회하고 **구독 1건의 실패가 다른 구독 처리를 막지 않도록** 개별 호출을 감싼다 —
 * [DeliverySubscriptionCycleService.processDueCycle] 이 이미 `REQUIRES_NEW` 로 자체 트랜잭션을 갖지만,
 * 예외가 처리 로직 밖까지(사전 점검을 통과한 뒤의 재고 레이스 등) 전파되는 극히 드문 경우까지
 * 대비해 이 순회 루프에서도 개별로 catch 한다(이중 안전망).
 */
@Service
@Transactional(readOnly = true)
class DeliverySubscriptionBillingService(
    private val subscriptionRepository: DeliverySubscriptionRepository,
    private val historyRepository: DeliverySubscriptionHistoryRepository,
    private val cycleService: DeliverySubscriptionCycleService,
    private val policyService: DeliverySubscriptionPolicyService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun runDueCycles(now: Instant = Instant.now()): DeliverySubscriptionBillingRunResult {
        val policy = policyService.currentPolicy()
        val dueIds = subscriptionRepository.findByStatusAndNextOrderAtLessThanEqual(DeliverySubscriptionStatus.ACTIVE, now)
            .mapNotNull { it.id }

        var orderCreated = 0
        var skippedOutOfStock = 0
        var skippedByUser = 0
        var paymentFailed = 0
        var autoPaused = 0
        var errored = 0

        dueIds.forEach { id ->
            try {
                when (cycleService.processDueCycle(id, now, policy)) {
                    DeliverySubscriptionHistoryResult.ORDER_CREATED -> orderCreated++
                    DeliverySubscriptionHistoryResult.SKIPPED_OUT_OF_STOCK -> skippedOutOfStock++
                    DeliverySubscriptionHistoryResult.SKIPPED_BY_USER -> skippedByUser++
                    DeliverySubscriptionHistoryResult.PAYMENT_FAILED -> paymentFailed++
                    DeliverySubscriptionHistoryResult.PAUSED_PRODUCT_UNAVAILABLE -> autoPaused++
                    null -> {} // 이미 처리됐거나 대상이 아니게 된 경우(레이스) — 집계하지 않음
                }
            } catch (ex: Exception) {
                log.error("정기배송 배치 처리 실패(subscriptionId={}) — 이 건만 격리하고 다음 건으로 계속 진행", id, ex)
                errored++
            }
        }

        log.info(
            "정기배송 배치 완료: 생성 {}건 / 품절스킵 {}건 / 사용자스킵 {}건 / 결제실패 {}건 / 자동정지 {}건 / 오류 {}건",
            orderCreated, skippedOutOfStock, skippedByUser, paymentFailed, autoPaused, errored,
        )
        return DeliverySubscriptionBillingRunResult(orderCreated, skippedOutOfStock, skippedByUser, paymentFailed, autoPaused, errored)
    }

    fun getHistories(userId: Long, subscriptionId: Long): List<DeliverySubscriptionHistoryResponse> {
        subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow { BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_FOUND) }
        return historyRepository.findBySubscriptionIdOrderByIdDesc(subscriptionId).map { DeliverySubscriptionHistoryResponse.from(it) }
    }

    fun getHistoriesForAdmin(subscriptionId: Long): List<DeliverySubscriptionHistoryResponse> =
        historyRepository.findBySubscriptionIdOrderByIdDesc(subscriptionId).map { DeliverySubscriptionHistoryResponse.from(it) }

    fun search(condition: AdminDeliverySubscriptionSearchCondition, pageable: Pageable): PageResponse<AdminDeliverySubscriptionResponse> {
        val page = subscriptionRepository.findPage(pageable) {
            select(entity(DeliverySubscription::class))
                .from(entity(DeliverySubscription::class))
                .whereAnd(
                    condition.status?.let { path(DeliverySubscription::status).eq(it) },
                )
                .orderBy(path(DeliverySubscription::id).desc())
        }
        return PageResponse.of(page) { AdminDeliverySubscriptionResponse.from(requireNotNull(it)) }
    }

    fun getDetailForAdmin(subscriptionId: Long): AdminDeliverySubscriptionResponse =
        AdminDeliverySubscriptionResponse.from(
            subscriptionRepository.findById(subscriptionId)
                .orElseThrow { BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_FOUND) },
        )
}

data class DeliverySubscriptionBillingRunResult(
    val orderCreated: Int,
    val skippedOutOfStock: Int,
    val skippedByUser: Int,
    val paymentFailed: Int,
    val autoPaused: Int,
    val errored: Int,
)
