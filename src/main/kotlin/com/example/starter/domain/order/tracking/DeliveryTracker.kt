package com.example.starter.domain.order.tracking

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/** 배송 조회 결과. [events] 는 오래된 순. */
data class TrackingResult(
    val delivered: Boolean,
    val events: List<TrackingEvent>,
)

data class TrackingEvent(
    val time: String,
    val location: String,
    val description: String,
)

/**
 * 택배사 배송 조회(ROADMAP 6.2). `delivery.tracker` 로 구현을 고른다(mock 기본, sweettracker).
 * 지원하지 않는 택배사면 null — 화면은 송장번호만 보여준다.
 */
interface DeliveryTracker {
    fun track(courier: String, trackingNumber: String): TrackingResult?
}

/** 외부 통신 없는 기본 구현. 송장 등록 사실만 한 줄로 돌려준다. */
@Component
@ConditionalOnProperty(prefix = "delivery", name = ["tracker"], havingValue = "mock", matchIfMissing = true)
class MockDeliveryTracker : DeliveryTracker {
    override fun track(courier: String, trackingNumber: String) =
        TrackingResult(delivered = false, events = listOf(TrackingEvent("", courier, "송장이 등록되었습니다")))
}

/**
 * 스마트택배(SweetTracker) 조회 API 어댑터(`/api/v1/trackingInfo?t_key&t_code&t_invoice`).
 * 판매자가 입력한 택배사 이름을 API 택배사 코드로 바꿔 조회하며, 코드는 `/api/v1/companylist` 기준이다.
 * 통신 오류는 null(조회 불가)로 처리해 주문 화면이 깨지지 않게 한다.
 */
@Component
@ConditionalOnProperty(prefix = "delivery", name = ["tracker"], havingValue = "sweettracker")
class SweetTrackerDeliveryTracker(
    @Value("\${delivery.sweettracker.api-key:}") private val apiKey: String,
    @Value("\${delivery.sweettracker.base-url:https://info.sweettracker.co.kr}") baseUrl: String,
    restClientBuilder: RestClient.Builder = RestClient.builder(),
) : DeliveryTracker {

    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    override fun track(courier: String, trackingNumber: String): TrackingResult? {
        val code = COURIER_CODES[courier.replace(" ", "")] ?: return null
        if (apiKey.isBlank()) return null
        return runCatching {
            val response = restClient.get()
                .uri("/api/v1/trackingInfo?t_key={key}&t_code={code}&t_invoice={invoice}", apiKey, code, trackingNumber)
                .retrieve()
                .body(Response::class.java)
                ?: return null
            if (response.status == false) return null // 잘못된 송장번호 등(msg 에 사유)
            TrackingResult(
                delivered = response.complete == true,
                events = response.trackingDetails.orEmpty().map { TrackingEvent(it.timeString.orEmpty(), it.where.orEmpty(), it.kind.orEmpty()) },
            )
        }.getOrNull()
    }

    data class Response(
        val status: Boolean? = null,
        val complete: Boolean? = null,
        val trackingDetails: List<Detail>? = null,
    )

    data class Detail(
        val kind: String? = null,
        val where: String? = null,
        val timeString: String? = null,
    )

    companion object {
        /** 판매자가 입력하는 택배사 이름(공백 무시) → 스마트택배 택배사 코드 */
        val COURIER_CODES = mapOf(
            "우체국택배" to "01",
            "CJ대한통운" to "04",
            "한진택배" to "05",
            "로젠택배" to "06",
            "롯데택배" to "08",
        )
    }
}
