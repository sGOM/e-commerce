package com.example.starter.domain.loyalty

import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.loyalty.dto.AdminLoyaltyTierResponse
import com.example.starter.domain.loyalty.dto.MyLoyaltyTierResponse
import com.example.starter.domain.loyalty.entity.LoyaltyTier
import com.example.starter.domain.loyalty.repository.LoyaltyTierProfileRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 로열티 등급 조회. 재계산은 [LoyaltyTierBatchService] 담당(이 서비스는 읽기 전용).
 * 프로필이 아직 없는 회원(신규 가입 등)은 BRONZE/0원으로 취급한다(에러로 다루지 않음).
 */
@Service
@Transactional(readOnly = true)
class LoyaltyTierService(
    private val loyaltyTierProfileRepository: LoyaltyTierProfileRepository,
    private val loyaltyTierProperties: LoyaltyTierProperties,
) {

    /** 마이페이지 "내 등급" — 다음 등급까지 남은 금액 포함. */
    fun getMyTier(userId: Long): MyLoyaltyTierResponse {
        val profile = loyaltyTierProfileRepository.findByUserId(userId).orElse(null)
        return MyLoyaltyTierResponse.of(profile, loyaltyTierProperties::nextTierThreshold)
    }

    /** 관리자 등급별 목록 조회. */
    fun searchForAdmin(tier: LoyaltyTier, pageable: Pageable): PageResponse<AdminLoyaltyTierResponse> {
        val page = loyaltyTierProfileRepository.findByTier(tier, pageable)
        return PageResponse.of(page) { AdminLoyaltyTierResponse.of(it.userId, it) }
    }

    /** 관리자 개별 회원 등급 조회. */
    fun getForAdmin(userId: Long): AdminLoyaltyTierResponse {
        val profile = loyaltyTierProfileRepository.findByUserId(userId).orElse(null)
        return AdminLoyaltyTierResponse.of(userId, profile)
    }
}
