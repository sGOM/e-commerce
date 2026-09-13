package com.example.starter.domain.seller

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.seller.dto.SellerResponse
import com.example.starter.domain.seller.entity.Seller
import com.example.starter.domain.seller.entity.SellerStatus
import com.example.starter.domain.seller.repository.SellerRepository
import com.example.starter.domain.user.repository.RoleRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자 입점 심사. 승인 시 상점을 [SellerStatus.ACTIVE] 로 전환하고 해당 회원에게 `ROLE_SELLER` 를 부여한다.
 */
@Service
@Transactional(readOnly = true)
class AdminSellerService(
    private val sellerRepository: SellerRepository,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
) {

    fun search(status: SellerStatus?): List<SellerResponse> {
        val sellers = if (status == null) {
            sellerRepository.findAll().sortedByDescending { it.id }
        } else {
            sellerRepository.findByStatusOrderByIdDesc(status)
        }
        return sellers.map { SellerResponse.from(it) }
    }

    /** 입점 승인/거절. 승인 시 ROLE_SELLER 부여 + ACTIVE, 거절 시 SUSPENDED. */
    @Transactional
    fun review(sellerId: Long, approved: Boolean): SellerResponse {
        val seller = sellerRepository.findById(sellerId)
            .orElseThrow { BusinessException(ErrorCode.SELLER_NOT_FOUND) }
        if (approved) {
            seller.status = SellerStatus.ACTIVE
            grantSellerRole(seller)
        } else {
            seller.status = SellerStatus.SUSPENDED
        }
        return SellerResponse.from(seller)
    }

    private fun grantSellerRole(seller: Seller) {
        val user = userRepository.findById(seller.userId)
            .orElseThrow { BusinessException(ErrorCode.USER_NOT_FOUND) }
        roleRepository.findByName(ROLE_SELLER)?.let { user.grantRole(it) }
    }

    companion object {
        private const val ROLE_SELLER = "ROLE_SELLER"
    }
}
