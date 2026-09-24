package com.example.starter.domain.gift

import com.example.starter.domain.gift.dto.GiftExpiryBatchResult
import com.example.starter.domain.gift.entity.GiftClaimStatus
import com.example.starter.domain.gift.repository.GiftClaimRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 선물 미수락 만료 배치(AC9/AC10). [GiftExpiryScheduler] 또는 관리자 수동 트리거가 호출한다.
 * 구독 배치([com.example.starter.domain.subscription.DeliverySubscriptionBillingService])와 동일하게
 * 대상 1건의 실패가 다른 건 처리를 막지 않도록 개별로 격리한다.
 */
@Service
@Transactional(readOnly = true)
class GiftExpiryBatchService(
    private val giftClaimRepository: GiftClaimRepository,
    private val processor: GiftExpiryProcessor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun expireDueClaims(now: Instant = Instant.now()): GiftExpiryBatchResult {
        val dueClaims = giftClaimRepository.findByStatusAndExpiresAtBefore(GiftClaimStatus.PENDING, now)
        var expired = 0
        var errored = 0
        dueClaims.forEach { claim ->
            try {
                processor.processExpiry(claim.id!!, now)
                expired++
            } catch (ex: Exception) {
                log.error("선물 만료 배치 처리 실패(giftClaimId={}) — 이 건만 격리하고 다음 건으로 계속 진행", claim.id, ex)
                errored++
            }
        }
        log.info("선물 만료 배치 완료: 만료취소 {}건 / 오류 {}건", expired, errored)
        return GiftExpiryBatchResult(expiredCount = expired, erroredCount = errored)
    }
}
