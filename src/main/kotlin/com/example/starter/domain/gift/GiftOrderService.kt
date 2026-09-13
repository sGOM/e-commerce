package com.example.starter.domain.gift

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.gift.dto.GiftClaimResponse
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.order.OrderService
import com.example.starter.domain.order.dto.OrderResponse
import com.example.starter.domain.order.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 구매자(회원) 소유권 기반 선물 주문 조작. 토큰 기반 수령자 플로우([GiftClaimService])와 역할을
 * 분리한다 — 여기는 항상 "내 주문"이라는 전제로 [orderId]+[userId] 로 접근한다.
 */
@Service
@Transactional(readOnly = true)
class GiftOrderService(
    private val orderService: OrderService,
    private val orderRepository: OrderRepository,
    private val giftClaimRepository: GiftClaimRepository,
) {

    /** 선물 링크 상태 조회(마이페이지 "보낸 선물" 목록/상세, 공유 링크 재확인용). */
    fun getStatus(userId: Long, orderId: Long): GiftClaimResponse {
        val order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        if (!order.isGift) throw BusinessException(ErrorCode.GIFT_NOT_A_GIFT_ORDER)
        val claim = giftClaimRepository.findByOrderId(orderId)
            .orElseThrow { BusinessException(ErrorCode.GIFT_CLAIM_NOT_FOUND) }
        return GiftClaimResponse.from(claim, order.orderNumber)
    }

    /**
     * 구매자가 수락 전 선물 주문을 취소한다(AC11 전반부 — 전액 환불). 취소 자체은 일반 주문 취소
     * ([OrderService.cancelOrder])를 그대로 재사용한다 — 배송 시작 전이면 결제 전/후 모두 취소
     * 가능한 기존 정책이 수락 여부와 무관하게 이미 "배송지 확정 전에는 발송 불가"([SubOrder.isShippable])
     * 가드로 안전하다. 이 메서드는 추가로 아직 대기 중인 선물 링크를 CANCELED 로 마감해 재사용을
     * 막는다(AC11 후반부 — 수락 이후는 일반 취소 정책과 동일하게 이 API 로도 동작하되 링크 상태는
     * 이미 CLAIMED 라 건드리지 않는다).
     */
    @Transactional
    fun cancelByBuyer(userId: Long, orderId: Long): OrderResponse {
        val order = orderRepository.findByIdAndUserId(orderId, userId)
            .orElseThrow { BusinessException(ErrorCode.ORDER_NOT_FOUND) }
        if (!order.isGift) throw BusinessException(ErrorCode.GIFT_NOT_A_GIFT_ORDER)

        val response = orderService.cancelOrder(userId, orderId)
        giftClaimRepository.findByOrderId(orderId)
            .filter { it.status == GiftClaimStatus.PENDING }
            .ifPresent { it.cancel() }
        return response
    }
}
