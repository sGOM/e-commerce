package com.example.starter.domain.seller

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.seller.dto.SellerResponse
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 입점(판매자) 신청. 회원이 상점을 신청하면 [SellerStatus.PENDING] 으로 생성되고,
 * 관리자 승인([com.example.starter.domain.seller.AdminSellerService])으로 영업이 시작된다.
 */
@Service
@Transactional(readOnly = true)
class SellerService(
    private val sellerRepository: SellerRepository,
) {

    @Transactional
    fun apply(userId: Long, storeName: String, description: String?): SellerResponse {
        if (sellerRepository.existsByUserId(userId)) {
            throw BusinessException(ErrorCode.ALREADY_SELLER)
        }
        val seller = sellerRepository.save(
            Seller(userId = userId, storeName = storeName, description = description, status = SellerStatus.PENDING),
        )
        return SellerResponse.from(seller)
    }

    fun getMyStore(userId: Long): SellerResponse =
        SellerResponse.from(findByUserId(userId))

    private fun findByUserId(userId: Long): Seller =
        sellerRepository.findByUserId(userId)
            ?: throw BusinessException(ErrorCode.SELLER_NOT_FOUND)
}
