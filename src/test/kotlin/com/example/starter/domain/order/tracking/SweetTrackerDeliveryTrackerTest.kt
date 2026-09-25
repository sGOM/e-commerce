package com.example.starter.domain.order.tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/** 스마트택배 어댑터 계약 테스트(외부 통신 없이 MockRestServiceServer). */
class SweetTrackerDeliveryTrackerTest {

    private val baseUrl = "https://tracker.test"

    private fun trackerWithServer(apiKey: String = "k-1"): Pair<SweetTrackerDeliveryTracker, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        return SweetTrackerDeliveryTracker(apiKey, baseUrl, builder) to server
    }

    @Test
    fun `택배사 이름을 코드로 바꿔 조회하고 이력을 돌려준다`() {
        val (tracker, server) = trackerWithServer()
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/api/v1/trackingInfo")))
            .andExpect(method(HttpMethod.GET))
            .andExpect(queryParam("t_key", "k-1"))
            .andExpect(queryParam("t_code", "04"))
            .andExpect(queryParam("t_invoice", "1234567890"))
            .andRespond(
                withSuccess(
                    """{"complete":true,"trackingDetails":[
                        {"kind":"집화처리","where":"서울","timeString":"2026-09-24 10:00:00"},
                        {"kind":"배달완료","where":"부산","timeString":"2026-09-25 14:00:00"}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = tracker.track("CJ 대한통운", "1234567890")!!

        assertTrue(result.delivered)
        assertEquals(TrackingEvent("2026-09-25 14:00:00", "부산", "배달완료"), result.events.last())
        server.verify()
    }

    @Test
    fun `지원하지 않는 택배사·키 없음·잘못된 송장·통신 오류는 조회 불가(null)다`() {
        assertNull(trackerWithServer().first.track("모르는택배", "1"))
        assertNull(trackerWithServer(apiKey = "").first.track("CJ대한통운", "1"))

        val (tracker, server) = trackerWithServer()
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/api/v1/trackingInfo")))
            .andRespond(withSuccess("""{"status":false,"msg":"운송장 미등록"}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("$baseUrl/api/v1/trackingInfo")))
            .andRespond(withServerError())

        assertNull(tracker.track("한진택배", "1"))
        assertNull(tracker.track("한진택배", "2"))
        server.verify()
    }
}
