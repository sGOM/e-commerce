package com.example.starter.domain.admin

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.common.response.ApiResponse
import com.example.starter.domain.order.entity.SubOrderStatus
import com.example.starter.domain.order.repository.SubOrderRepository
import com.example.starter.domain.user.repository.UserRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Date
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class DailySales(val date: LocalDate, val orderCount: Long, val gmv: Long)

data class AdminDashboardResponse(
    val from: LocalDate,
    val to: LocalDate,
    val orderCount: Long,
    val gmv: Long,
    val newUserCount: Long,
    val daily: List<DailySales>,
)

/**
 * 관리자 대시보드 API (ROADMAP 5.3). 기간은 한국 시간 날짜(`yyyy-MM-dd`), 생략 시 오늘까지 최근 30일, 최대 366일.
 *
 * GMV 는 판매자 대시보드·정산과 같은 기준(결제 이후 상태, 취소 제외 하위 주문의 상품 합계)이라
 * 쿠폰/포인트 할인 전 금액이다.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
class AdminDashboardController(
    private val subOrderRepository: SubOrderRepository,
    private val userRepository: UserRepository,
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun get(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): ApiResponse<AdminDashboardResponse> {
        val end = to ?: LocalDate.now(ZONE)
        val start = from ?: end.minusDays(29)
        val days = ChronoUnit.DAYS.between(start, end) + 1
        if (days !in 1..MAX_DAYS) {
            throw BusinessException(ErrorCode.INVALID_INPUT, "기간은 시작일 ≤ 종료일, 최대 ${MAX_DAYS}일입니다.")
        }
        val fromInstant = start.atStartOfDay(ZONE).toInstant()
        val toInstant = end.plusDays(1).atStartOfDay(ZONE).toInstant()

        val byDate = subOrderRepository.dailySales(SOLD, fromInstant, toInstant).associate {
            (it[0] as Date).toLocalDate() to DailySales((it[0] as Date).toLocalDate(), (it[1] as Number).toLong(), (it[2] as Number).toLong())
        }
        val daily = (0 until days).map { start.plusDays(it) }.map { byDate[it] ?: DailySales(it, 0, 0) }

        return ApiResponse.success(
            AdminDashboardResponse(
                from = start,
                to = end,
                orderCount = daily.sumOf { it.orderCount },
                gmv = daily.sumOf { it.gmv },
                newUserCount = userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(fromInstant, toInstant),
                daily = daily,
            ),
        )
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private const val MAX_DAYS = 366L

        // 판매자 대시보드·정산 대상과 같은 기준: 결제 이후 상태, 취소 제외
        private val SOLD = listOf(
            SubOrderStatus.PAID,
            SubOrderStatus.PREPARING,
            SubOrderStatus.SHIPPED,
            SubOrderStatus.DELIVERED,
        ).map { it.name }
    }
}
