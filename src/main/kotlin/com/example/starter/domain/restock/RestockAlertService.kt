package com.example.starter.domain.restock

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.admin.dto.PageResponse
import com.example.starter.domain.catalog.repository.ProductOptionRepository
import com.example.starter.domain.notification.NotificationService
import com.example.starter.domain.notification.entity.NotificationType
import com.example.starter.domain.restock.dto.RestockAlertResponse
import com.example.starter.domain.restock.entity.RestockAlert
import com.example.starter.domain.restock.entity.RestockAlertStatus
import com.example.starter.domain.restock.repository.RestockAlertRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 재입고 알림 신청/취소 및 재고 보충 트리거 처리(`docs/planning/restock-alert.md`).
 */
@Service
@Transactional(readOnly = true)
class RestockAlertService(
    private val restockAlertRepository: RestockAlertRepository,
    private val productOptionRepository: ProductOptionRepository,
    private val notificationService: NotificationService,
) {

    /** 품절 옵션에 알림 신청(US-1). 가용재고가 있으면(AC1) / 이미 PENDING 이면(AC2) 거절한다. */
    @Transactional
    fun subscribe(userId: Long, optionId: Long): RestockAlertResponse {
        val option = productOptionRepository.findWithProductAndInventoryById(optionId)
            .orElseThrow { BusinessException(ErrorCode.PRODUCT_OPTION_NOT_FOUND) }
        val available = option.inventory?.available ?: 0
        if (available > 0) {
            throw BusinessException(ErrorCode.RESTOCK_ALERT_NOT_ALLOWED)
        }
        if (restockAlertRepository.existsByUserIdAndOptionIdAndStatus(userId, optionId, RestockAlertStatus.PENDING)) {
            throw BusinessException(ErrorCode.RESTOCK_ALERT_ALREADY_EXISTS)
        }
        val alert = restockAlertRepository.save(RestockAlert(userId = userId, optionId = optionId))
        return RestockAlertResponse.from(alert, option)
    }

    /** 신청 취소(US-2/AC4). 본인의 PENDING 신청만 취소 가능 — 없으면 404. */
    @Transactional
    fun cancel(userId: Long, optionId: Long) {
        val alert = restockAlertRepository
            .findByUserIdAndOptionIdAndStatus(userId, optionId, RestockAlertStatus.PENDING)
            .orElseThrow { BusinessException(ErrorCode.RESTOCK_ALERT_NOT_FOUND) }
        alert.cancel()
    }

    /** 내 신청 목록(마이페이지). 상품/옵션명을 배치 조회로 채운다(리뷰 도메인의 배치 조회 패턴과 동일). */
    fun getMyAlerts(userId: Long, pageable: Pageable): PageResponse<RestockAlertResponse> {
        val page = restockAlertRepository.findByUserIdOrderByIdDesc(userId, pageable)
        val optionsById = productOptionRepository
            .findWithProductByIdIn(page.content.map { it.optionId }.distinct())
            .associateBy { it.id }
        return PageResponse.of(page) { alert -> RestockAlertResponse.from(alert, optionsById[alert.optionId]) }
    }

    /**
     * 재고 보충 트리거(`InventoryRestockedEvent` 구독측 호출 대상). 옵션의 PENDING 신청 전체를
     * NOTIFIED 로 전이하고 인앱 알림을 생성한다(AC5).
     *
     * - 상품이 숨김/삭제(비공개) 면 발송하지 않고 PENDING 을 그대로 둔다(오픈 이슈 1 결정 — 상품이 다시
     *   공개되고 재고가 또 조정될 때 재평가된다. 별도 배치/만료 처리는 두지 않아 과설계를 피한다).
     * - 이벤트 발행과 이 메서드 실행 사이(AFTER_COMMIT + @Async) 재고가 다시 0 이 될 수 있어, 발송
     *   직전 가용재고를 한 번 더 확인한다(available <= 0 이면 스킵, PENDING 유지 — 다음 실제 재입고
     *   시점에 재평가).
     */
    @Transactional
    fun notifyPending(optionId: Long) {
        val option = productOptionRepository.findWithProductAndInventoryById(optionId).orElse(null) ?: return
        val product = option.product
        if (!product.status.isVisible) return
        val available = option.inventory?.available ?: 0
        if (available <= 0) return

        val pending = restockAlertRepository.findPendingForUpdate(optionId)
        if (pending.isEmpty()) return

        val now = Instant.now()
        pending.forEach { alert ->
            alert.markNotified(now)
            notificationService.notify(
                userId = alert.userId,
                type = NotificationType.RESTOCK,
                title = "재입고 알림",
                body = "'${product.name}' 상품(${option.name})이 재입고되었습니다.",
                linkUrl = "/products/${product.id}",
            )
        }
    }
}
