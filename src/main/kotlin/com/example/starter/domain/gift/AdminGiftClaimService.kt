package com.example.starter.domain.gift

import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.gift.dto.GiftClaimResponse
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.repository.GiftClaimRepository
import com.example.starter.domain.order.repository.OrderRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 선물 링크 모니터링(`GET /api/admin/gift-claims`, 만료 임박/장기 미수락 파악 등).
 */
@Service
@Transactional(readOnly = true)
class AdminGiftClaimService(
    private val giftClaimRepository: GiftClaimRepository,
    private val orderRepository: OrderRepository,
) {

    fun search(status: GiftClaimStatus?, pageable: Pageable): PageResponse<GiftClaimResponse> {
        val page = if (status != null) {
            giftClaimRepository.findByStatusOrderByIdDesc(status, pageable)
        } else {
            giftClaimRepository.findAllByOrderByIdDesc(pageable)
        }
        return PageResponse.of(page) { claim ->
            val orderNumber = orderRepository.findById(claim.orderId).map { it.orderNumber }.orElse("")
            GiftClaimResponse.from(claim, orderNumber)
        }
    }
}
