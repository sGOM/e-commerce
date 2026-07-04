package com.example.starter.domain.restock

import com.example.starter.domain.catalog.event.InventoryRestockedEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 재고 보충 이벤트 구독. 재고 갱신 트랜잭션이 **커밋된 이후**([TransactionPhase.AFTER_COMMIT])에만
 * 실행되며, 전용 스레드 풀에서 비동기로 처리해 재고 갱신 응답 지연을 없앤다(`AuditLogService` 의
 * `@Async` 패턴과 동일 원칙, `docs/planning/restock-alert.md` AC7).
 *
 * 롤백된 재고 조정(예외 발생)은 이벤트 자체가 폐기되므로 잘못된 알림이 나갈 위험이 없다.
 * 참고: https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
 */
@Component
class RestockAlertEventListener(
    private val restockAlertService: RestockAlertService,
) {

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInventoryRestocked(event: InventoryRestockedEvent) {
        restockAlertService.notifyPending(event.optionId)
    }
}
