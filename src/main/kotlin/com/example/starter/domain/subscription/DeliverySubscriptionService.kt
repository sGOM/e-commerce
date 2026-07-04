package com.example.starter.domain.subscription

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.billing.gateway.BillingKeyGateway
import com.example.starter.domain.billing.gateway.IssueBillingKeyCommand
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.subscription.dto.CreateDeliverySubscriptionRequest
import com.example.starter.domain.subscription.dto.DeliverySubscriptionBillingKeyResponse
import com.example.starter.domain.subscription.dto.DeliverySubscriptionResponse
import com.example.starter.domain.subscription.dto.RegisterDeliverySubscriptionBillingKeyRequest
import com.example.starter.domain.subscription.entity.DeliverySubscription
import com.example.starter.domain.subscription.entity.DeliverySubscriptionBillingKey
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import com.example.starter.domain.subscription.repository.DeliverySubscriptionBillingKeyRepository
import com.example.starter.domain.subscription.repository.DeliverySubscriptionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 회원 정기배송 등록/조회/일시정지/재개/해지/스킵(회원 전용, 게스트 미지원 — 정기결제는 카드 등록이
 * 필요해 게스트 체크아웃과 어울리지 않음). 회차 자동 처리 자체는 [DeliverySubscriptionCycleService]/
 * [DeliverySubscriptionBillingService] 가 맡고, 이 서비스는 등록 즉시 시작(AC2)일 때만 첫 회차 처리를
 * 직접 호출한다.
 */
@Service
@Transactional(readOnly = true)
class DeliverySubscriptionService(
    private val subscriptionRepository: DeliverySubscriptionRepository,
    private val billingKeyRepository: DeliverySubscriptionBillingKeyRepository,
    private val billingKeyGateway: BillingKeyGateway,
    private val productOptionRepository: ProductOptionRepository,
    private val policyService: DeliverySubscriptionPolicyService,
    private val cycleService: DeliverySubscriptionCycleService,
) {

    /** 카드 등록(빌링키 발급). 이미 등록되어 있으면 교체한다. */
    @Transactional
    fun registerBillingKey(userId: Long, request: RegisterDeliverySubscriptionBillingKeyRequest): DeliverySubscriptionBillingKeyResponse {
        val result = billingKeyGateway.issueBillingKey(IssueBillingKeyCommand(userId, requireNotNull(request.cardNumber)))
        if (!result.success) {
            throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_BILLING_KEY_INVALID, result.message)
        }
        val billingKey = requireNotNull(result.billingKey)
        val cardLast4 = requireNotNull(result.cardLast4)
        val saved = billingKeyRepository.findByUserId(userId)?.also { it.replace(billingKey, cardLast4) }
            ?: billingKeyRepository.save(DeliverySubscriptionBillingKey(userId = userId, gatewayBillingKey = billingKey, cardLast4 = cardLast4))
        return DeliverySubscriptionBillingKeyResponse.from(saved)
    }

    /** 정기배송 등록(AC1). 결제수단이 미리 등록돼 있어야 한다([registerBillingKey]). */
    @Transactional
    fun register(userId: Long, request: CreateDeliverySubscriptionRequest): DeliverySubscriptionResponse {
        billingKeyRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_BILLING_KEY_NOT_REGISTERED)
        val optionId = requireNotNull(request.optionId)
        val option = productOptionRepository.findWithProductAndInventoryById(optionId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }
        val cycleDays = requireNotNull(request.cycleDays)
        val now = Instant.now()

        val subscription = DeliverySubscription(
            userId = userId,
            optionId = optionId,
            quantity = requireNotNull(request.quantity),
            cycleDays = cycleDays,
            ordererName = requireNotNull(request.ordererName),
            ordererPhone = requireNotNull(request.ordererPhone),
            ordererEmail = requireNotNull(request.ordererEmail),
            shippingAddress = requireNotNull(request.shippingAddress).toEmbeddable(),
        )
        subscription.nextOrderAt = if (request.startImmediately) now else now.plusSeconds(cycleDays * SECONDS_PER_DAY)
        subscriptionRepository.save(subscription)

        // 즉시 시작(AC2, 기본값) — 등록 트랜잭션에 그대로 합류해 첫 회차를 처리한다(방금 저장한 아직
        // 미커밋 행을 그대로 재사용, DeliverySubscriptionCycleService 클래스 docs 참고).
        if (request.startImmediately) {
            cycleService.processImmediateFirstCycle(subscription, now, policyService.currentPolicy())
        }
        return DeliverySubscriptionResponse.from(subscription, option.product.name, option.name)
    }

    fun getMy(userId: Long): List<DeliverySubscriptionResponse> {
        val subscriptions = subscriptionRepository.findByUserIdOrderByIdDesc(userId)
        if (subscriptions.isEmpty()) return emptyList()
        val options = productOptionRepository.findWithProductByIdIn(subscriptions.map { it.optionId }).associateBy { it.id }
        return subscriptions.map { s ->
            val option = options[s.optionId]
            DeliverySubscriptionResponse.from(s, option?.product?.name ?: "상품 정보 없음", option?.name ?: "-")
        }
    }

    /** 일시정지(AC3). ACTIVE 상태에서만 가능. */
    @Transactional
    fun pause(userId: Long, subscriptionId: Long): DeliverySubscriptionResponse {
        val subscription = findOwned(userId, subscriptionId)
        if (subscription.status != DeliverySubscriptionStatus.ACTIVE) {
            throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_ACTIVE)
        }
        subscription.pauseByUser()
        return toResponse(subscription)
    }

    /** 재개(AC3). PAUSED 상태에서만 가능 — 다음 청구일부터 재개된다(장기 정지 시 재개 시점 기준으로 재산정). */
    @Transactional
    fun resume(userId: Long, subscriptionId: Long): DeliverySubscriptionResponse {
        val subscription = findOwned(userId, subscriptionId)
        if (subscription.status != DeliverySubscriptionStatus.PAUSED) {
            throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_PAUSED)
        }
        subscription.resume(Instant.now())
        return toResponse(subscription)
    }

    /** 해지(AC4). 예정된 다음 회차부터 생성되지 않는다. */
    @Transactional
    fun cancel(userId: Long, subscriptionId: Long): DeliverySubscriptionResponse {
        val subscription = findOwned(userId, subscriptionId)
        if (subscription.status == DeliverySubscriptionStatus.CANCELED) {
            throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_CANCELABLE)
        }
        subscription.cancel(Instant.now())
        return toResponse(subscription)
    }

    /** 다음 회차 1회 건너뛰기 요청(AC5). 다음 배송일 T일 전(정책값)까지만 허용한다. */
    @Transactional
    fun skipNext(userId: Long, subscriptionId: Long): DeliverySubscriptionResponse {
        val subscription = findOwned(userId, subscriptionId)
        if (subscription.status != DeliverySubscriptionStatus.ACTIVE) {
            throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_ACTIVE)
        }
        val policy = policyService.currentPolicy()
        val deadline = subscription.nextOrderAt.minusSeconds(policy.skipDeadlineDays * SECONDS_PER_DAY)
        if (Instant.now().isAfter(deadline)) {
            throw BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_SKIP_WINDOW_CLOSED)
        }
        subscription.skipRequested = true
        return toResponse(subscription)
    }

    private fun findOwned(userId: Long, subscriptionId: Long): DeliverySubscription =
        subscriptionRepository.findByIdAndUserId(subscriptionId, userId)
            .orElseThrow { BusinessException(ErrorCode.DELIVERY_SUBSCRIPTION_NOT_FOUND) }

    private fun toResponse(subscription: DeliverySubscription): DeliverySubscriptionResponse {
        val option = productOptionRepository.findWithProductAndInventoryById(subscription.optionId).orElse(null)
        return DeliverySubscriptionResponse.from(subscription, option?.product?.name ?: "상품 정보 없음", option?.name ?: "-")
    }

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
    }
}
