package com.example.starter.domain.membership

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 주기 자동 정기결제. `membership.scheduler.enabled=true` 일 때만 빈으로 등록된다
 * ([com.example.starter.domain.settlement.SettlementScheduler] 와 동일 패턴).
 *
 * [MembershipBillingService.runDueBilling] 은 "다음 결제일 도래 여부"로 대상을 매번 다시 조회하므로
 * 멱등하다 — 같은 멤버십이 이미 갱신됐다면(`nextBillingAt` 이 미래로 이동) 다음 실행 때는 대상에서
 * 자연히 제외된다. 관리자 수동 트리거([AdminMembershipController])와도 동일 로직을 공유한다.
 */
@Component
@ConditionalOnProperty(prefix = "membership.scheduler", name = ["enabled"], havingValue = "true")
class MembershipBillingScheduler(
    private val membershipBillingService: MembershipBillingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        cron = "\${membership.scheduler.cron:0 0 3 * * *}",
        zone = "\${membership.scheduler.zone:Asia/Seoul}",
    )
    fun run() {
        val result = membershipBillingService.runDueBilling()
        log.info("멤버십 정기결제 스케줄러 실행 완료: {}", result)
    }
}
