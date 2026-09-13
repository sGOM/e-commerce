package com.example.starter.domain.restock.dto

import com.example.starter.domain.catalog.entity.ProductOption
import com.example.starter.domain.restock.entity.RestockAlert
import com.example.starter.domain.restock.entity.RestockAlertStatus
import java.time.Instant

/** 재입고 알림 신청 응답. 마이페이지 목록에서는 [option] 을 함께 배치 조회해 상품/옵션명을 채운다. */
data class RestockAlertResponse(
    val id: Long,
    val optionId: Long,
    val productId: Long?,
    val productName: String?,
    val optionName: String?,
    val status: RestockAlertStatus,
    val createdAt: Instant,
    val notifiedAt: Instant?,
) {
    companion object {
        fun from(alert: RestockAlert, option: ProductOption? = null) = RestockAlertResponse(
            id = requireNotNull(alert.id),
            optionId = alert.optionId,
            productId = option?.product?.id,
            productName = option?.product?.name,
            optionName = option?.name,
            status = alert.status,
            createdAt = alert.createdAt,
            notifiedAt = alert.notifiedAt,
        )
    }
}
