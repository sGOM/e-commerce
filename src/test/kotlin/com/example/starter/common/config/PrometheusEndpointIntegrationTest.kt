package com.example.starter.common.config

import com.example.starter.support.AbstractIntegrationTest
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@AutoConfigureMockMvc
@AutoConfigureObservability // 테스트는 기본적으로 메트릭 exporter 를 끄므로 명시적으로 켠다
class PrometheusEndpointIntegrationTest : AbstractIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `프로메테우스가 인증 없이 JVM·커넥션풀 지표를 수집할 수 있다`() {
        mockMvc.get("/actuator/prometheus").andExpect {
            status { isOk() }
            content { string(containsString("jvm_memory_used_bytes")) }
            content { string(containsString("hikaricp_connections_active")) }
        }
    }
}
