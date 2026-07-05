package com.example.starter.domain.loyalty

import com.example.starter.domain.loyalty.dto.LoyaltyTierBatchResult
import com.example.starter.domain.loyalty.entity.LoyaltyTierProfile
import com.example.starter.domain.loyalty.repository.LoyaltyTierProfileRepository
import com.example.starter.domain.order.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset

/**
 * 로열티 등급 재계산 배치. [LoyaltyTierScheduler] 또는 관리자 수동 트리거가 호출한다
 * (`docs/planning` 요청사항 — 최근 12개월 순구매액 롤링 윈도우 재집계, 강등 포함).
 *
 * [com.example.starter.domain.gift.GiftExpiryBatchService] 와 동일하게 대상 1건의 실패가 다른 건
 * 처리를 막지 않도록 개별로 격리한다.
 */
@Service
@Transactional(readOnly = true)
class LoyaltyTierBatchService(
    private val orderRepository: OrderRepository,
    private val loyaltyTierProfileRepository: LoyaltyTierProfileRepository,
    private val loyaltyTierProperties: LoyaltyTierProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 전 회원 대상 재계산. 대상 회원 id 는 (1) 최근 윈도우 내 유효 구매 실적이 있는 회원과
     * (2) 이미 등급 프로필이 있는 회원(윈도우 이탈로 강등 재평가가 필요할 수 있음)의 합집합이다.
     */
    fun recalculateAll(now: Instant = Instant.now()): LoyaltyTierBatchResult {
        val since = now.atZone(ZoneOffset.UTC).minusMonths(loyaltyTierProperties.lookbackMonths).toInstant()
        val netAmountByUser = orderRepository.aggregateNetPurchaseAmountSince(since)
            .associate { row -> (row[0] as Number).toLong() to (row[1] as Number).toLong() }
        val existingProfileUserIds = loyaltyTierProfileRepository.findAllUserIds()
        val targetUserIds = (netAmountByUser.keys + existingProfileUserIds).toSet()

        var upgraded = 0
        var downgraded = 0
        var unchanged = 0
        var errored = 0
        targetUserIds.forEach { userId ->
            try {
                when (recalculateOne(userId, netAmountByUser[userId] ?: 0L, now)) {
                    RecalcResult.UPGRADED -> upgraded++
                    RecalcResult.DOWNGRADED -> downgraded++
                    RecalcResult.UNCHANGED -> unchanged++
                }
            } catch (ex: Exception) {
                log.error("로열티 등급 재계산 실패(userId={}) — 이 건만 격리하고 다음 건으로 계속 진행", userId, ex)
                errored++
            }
        }
        val result = LoyaltyTierBatchResult(upgraded, downgraded, unchanged, errored)
        log.info("로열티 등급 재계산 배치 완료: {}", result)
        return result
    }

    @Transactional
    fun recalculateOne(userId: Long, netPurchaseAmount: Long, now: Instant): RecalcResult {
        val profile = loyaltyTierProfileRepository.findByUserId(userId)
            .orElseGet { loyaltyTierProfileRepository.save(LoyaltyTierProfile(userId = userId)) }
        val before = profile.tier
        val newTier = loyaltyTierProperties.resolveTier(netPurchaseAmount)
        profile.applyRecalculation(newTier, netPurchaseAmount, now)
        return when {
            newTier.ordinal > before.ordinal -> RecalcResult.UPGRADED
            newTier.ordinal < before.ordinal -> RecalcResult.DOWNGRADED
            else -> RecalcResult.UNCHANGED
        }
    }

    enum class RecalcResult { UPGRADED, DOWNGRADED, UNCHANGED }
}
