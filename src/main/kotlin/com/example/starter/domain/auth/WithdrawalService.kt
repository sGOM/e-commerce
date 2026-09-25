package com.example.starter.domain.auth

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.membership.entity.MembershipStatus
import com.example.starter.domain.membership.repository.MembershipBillingKeyRepository
import com.example.starter.domain.membership.repository.MembershipRepository
import com.example.starter.domain.order.OrderService
import com.example.starter.domain.order.entity.OrderStatus
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.OrderRepository
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.subscription.entity.DeliverySubscriptionStatus
import com.example.starter.domain.subscription.repository.DeliverySubscriptionBillingKeyRepository
import com.example.starter.domain.subscription.repository.DeliverySubscriptionRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 회원 탈퇴(ROADMAP 3.3, 2026-09-25 확정: soft delete). 사용자 행과 주문·결제·정산 이력은 남기고
 * 상태를 WITHDRAWN 으로 바꿔 로그인(자체·소셜)을 막는다. 개인정보 익명화는 하지 않는다(정책 결정).
 *
 * 함께 정리하는 것: 미결제 주문 취소(재고 예약 해제), 멤버십 해지, 정기배송 해지, 저장된 결제수단(빌링키) 삭제.
 * 결제 후 배송이 끝나지 않은 주문이 있거나 판매자 계정이면 거부한다 — 환불·정산 책임이 남기 때문이다.
 */
@Service
@Transactional(readOnly = true)
class WithdrawalService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val orderRepository: OrderRepository,
    private val subOrderRepository: SubOrderRepository,
    private val orderService: OrderService,
    private val membershipRepository: MembershipRepository,
    private val membershipBillingKeyRepository: MembershipBillingKeyRepository,
    private val deliverySubscriptionRepository: DeliverySubscriptionRepository,
    private val deliverySubscriptionBillingKeyRepository: DeliverySubscriptionBillingKeyRepository,
) {

    @Transactional
    fun withdraw(userId: Long, password: String?) {
        val user = userRepository.findWithRolesById(userId) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
        val hash = user.password
        if (hash != null && !passwordEncoder.matches(password.orEmpty(), hash)) {
            throw BusinessException(ErrorCode.PASSWORD_MISMATCH)
        }
        if (user.roles.any { it.name == "ROLE_SELLER" }) {
            throw BusinessException(ErrorCode.WITHDRAWAL_SELLER_NOT_ALLOWED)
        }
        if (subOrderRepository.existsByUserIdAndStatusIn(userId, IN_PROGRESS)) {
            throw BusinessException(ErrorCode.WITHDRAWAL_ORDERS_IN_PROGRESS)
        }

        val now = Instant.now()
        orderRepository.findByUserIdAndStatus(userId, OrderStatus.CREATED).forEach { orderService.cancelOrder(userId, it.id!!) }
        membershipRepository.findByUserId(userId)
            ?.takeIf { it.status == MembershipStatus.ACTIVE || it.status == MembershipStatus.PAST_DUE }
            ?.scheduleCancel(now)
        deliverySubscriptionRepository.findByUserIdOrderByIdDesc(userId)
            .filter { it.status != DeliverySubscriptionStatus.CANCELED }
            .forEach { it.cancel(now) }
        membershipBillingKeyRepository.findByUserId(userId)?.let(membershipBillingKeyRepository::delete)
        deliverySubscriptionBillingKeyRepository.findByUserId(userId)?.let(deliverySubscriptionBillingKeyRepository::delete)
        user.withdraw(now)
    }

    private companion object {
        val IN_PROGRESS = listOf(SubOrderStatus.PAID, SubOrderStatus.PREPARING, SubOrderStatus.SHIPPED)
    }
}
