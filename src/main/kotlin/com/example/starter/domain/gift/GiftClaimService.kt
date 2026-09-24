package com.example.starter.domain.gift

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.gift.dto.GiftClaimRequest
import com.example.starter.domain.gift.dto.GiftClaimResponse
import com.example.starter.domain.gift.dto.GiftPreviewResponse
import com.example.starter.domain.gift.entity.GiftClaim
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.entity.GiftPolicy
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.gift.repository.GiftPolicyRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.order.entity.Order
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 선물 링크(토큰) 발급 및 수령자 플로우(미리보기/수락). 토큰 하나로만 인가되는 공개 API 대상이며
 * (`docs/planning/gift-order.md` AC6/AC8, 비회원 수령자 지원), 구매자 소유권 검사가 필요한 작업은
 * [com.example.starter.domain.gift.GiftOrderService] 가 담당한다(역할 분리).
 */
@Service
@Transactional(readOnly = true)
class GiftClaimService(
    private val giftClaimRepository: GiftClaimRepository,
    private val orderRepository: OrderRepository,
    private val giftPolicyRepository: GiftPolicyRepository,
    private val notificationService: NotificationService,
) {

    /**
     * 선물 주문 생성 직후 호출된다([com.example.starter.domain.order.OrderService.createFromCart]).
     * 정책 만료기한(§9 오픈이슈 #2)만큼 뒤를 만료 시각으로 잡는다.
     */
    @Transactional
    fun createForOrder(orderId: Long, now: Instant = Instant.now()): GiftClaim {
        val policy = currentPolicy()
        val claim = GiftClaim(
            orderId = orderId,
            token = generateToken(),
            expiresAt = policy.expiresAtFrom(now),
        )
        return giftClaimRepository.save(claim)
    }

    /** 선물 미리보기(AC6) — 로그인 불필요. 만료/수락 여부와 무관하게 상태 확인 차원에서 항상 보여준다. */
    fun getPreview(token: String): GiftPreviewResponse {
        val claim = findByToken(token)
        val order = findOrder(claim.orderId)
        return GiftPreviewResponse.from(claim, order)
    }

    /**
     * 수령자 수락(배송지 입력, AC7) — 로그인 불필요. 성공 시 [Order.shippingAddress] 를 채워 배송
     * 준비가 시작될 수 있는 상태로 전이시키고([Order.assignGiftShippingAddress]), 구매자에게 알린다.
     */
    @Transactional
    fun claim(token: String, request: GiftClaimRequest, now: Instant = Instant.now()): GiftClaimResponse {
        val claim = findByToken(token)
        when {
            claim.status == GiftClaimStatus.CLAIMED -> throw BusinessException(ErrorCode.GIFT_CLAIM_ALREADY_CLAIMED)
            claim.status == GiftClaimStatus.PENDING && now.isAfter(claim.expiresAt) -> {
                claim.expire()
                throw BusinessException(ErrorCode.GIFT_CLAIM_EXPIRED)
            }
            claim.status != GiftClaimStatus.PENDING -> throw BusinessException(ErrorCode.GIFT_CLAIM_NOT_CLAIMABLE)
        }

        val order = findOrder(claim.orderId)
        if (order.status == OrderStatus.CANCELED) {
            throw BusinessException(ErrorCode.GIFT_CLAIM_NOT_CLAIMABLE, "이미 취소된 선물 주문입니다.")
        }
        order.assignGiftShippingAddress(requireNotNull(request.shippingAddress).toEmbeddable())
        claim.claim(now)

        order.userId?.let {
            notificationService.notify(
                userId = it,
                type = NotificationType.GIFT,
                title = "선물이 수락되었습니다",
                body = "받는 분이 배송지를 입력해 배송 준비가 시작됩니다. (주문번호: ${order.orderNumber})",
                linkUrl = "/orders/${order.id}",
            )
        }
        return GiftClaimResponse.from(claim, order.orderNumber)
    }

    /** 주문이 결제 전에 취소될 때(미결제 만료) 대기 중인 수령 링크를 함께 마감한다. */
    @Transactional
    fun cancelForOrder(orderId: Long) {
        giftClaimRepository.findByOrderId(orderId).ifPresent { it.cancel() }
    }

    private fun findByToken(token: String): GiftClaim =
        giftClaimRepository.findByToken(token).orElseThrow { BusinessException(ErrorCode.GIFT_CLAIM_NOT_FOUND) }

    private fun findOrder(orderId: Long): Order =
        orderRepository.findById(orderId).orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }

    private fun currentPolicy() =
        giftPolicyRepository.findFirstByOrderByIdAsc() ?: giftPolicyRepository.save(GiftPolicy())

    /** URL 에 노출되는 유일한 인가 수단이라 추측 불가능한 무작위 토큰을 쓴다(UUID 2개 조합, 하이픈 제거). */
    private fun generateToken(): String =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").take(8)
}
