package com.example.starter.domain.returns

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.order.OrderService
import com.example.starter.domain.returns.dto.ReturnResponse
import com.example.starter.domain.returns.entity.OrderReturn
import com.example.starter.domain.returns.entity.ReturnReason
import com.example.starter.domain.returns.repository.OrderReturnRepository
import com.example.starter.domain.seller.repository.SellerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 판매자 반품 처리: 승인(회수 시작) → 검수 완료(환불) / 거절. 본인 판매분만 다룬다. */
@Service
@Transactional(readOnly = true)
class SellerReturnService(
    private val orderReturnRepository: OrderReturnRepository,
    private val sellerRepository: SellerRepository,
    private val orderService: OrderService,
) {

    fun getMyReturns(userId: Long): List<ReturnResponse> =
        orderReturnRepository.findBySubOrderSellerIdOrderByIdDesc(sellerId(userId)).map { ReturnResponse.from(it) }

    @Transactional
    fun approve(userId: Long, returnId: Long): ReturnResponse {
        val request = findOwned(userId, returnId)
        request.approve()
        return ReturnResponse.from(request)
    }

    /** 검수 통과 — 요청 시점에 스냅샷한 환불액을 환불하고, 단순변심 회수품만 재고로 되돌린다. */
    @Transactional
    fun complete(userId: Long, returnId: Long): ReturnResponse {
        val request = findOwned(userId, returnId)
        request.complete()
        orderService.completeReturn(
            request.subOrder,
            request.refundAmount,
            sellerReturnFee = request.returnFee,
            restock = request.reason == ReturnReason.CHANGE_OF_MIND,
            returnId = returnId,
        )
        return ReturnResponse.from(request)
    }

    @Transactional
    fun reject(userId: Long, returnId: Long): ReturnResponse {
        val request = findOwned(userId, returnId)
        request.reject()
        return ReturnResponse.from(request)
    }

    private fun findOwned(userId: Long, returnId: Long): OrderReturn =
        orderReturnRepository.findByIdAndSubOrderSellerId(returnId, sellerId(userId))
            .orElseThrow { BusinessException(ErrorCode.RETURN_NOT_FOUND) }

    private fun sellerId(userId: Long): Long =
        (
            sellerRepository.findByUserId(userId)
                ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)
            ).id!!
}
