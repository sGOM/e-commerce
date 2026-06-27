package com.example.starter.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * 통합 테스트 공통 베이스.
 *
 * 기본적으로 **PostgreSQL 컨테이너를 싱글톤으로 한 번만** 기동해 전체 테스트가 공유한다
 * (H2 대신 실제 PostgreSQL → 운영 환경 일치). 컨테이너는 Ryuk 가 JVM 종료 시 정리한다.
 *
 * 단, 일부 환경(예: Docker Desktop 과 docker-java 비호환)에서는 Testcontainers 가
 * Docker 데몬에 접속하지 못한다. 이때는 외부에서 이미 띄운 PostgreSQL 을 가리키도록
 * 시스템 프로퍼티/환경변수로 우회할 수 있다:
 *
 *   -Dit.datasource.url=jdbc:postgresql://localhost:5433/starter
 *   (또는 환경변수 IT_DATASOURCE_URL / IT_DATASOURCE_USERNAME / IT_DATASOURCE_PASSWORD)
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractIntegrationTest {

    companion object {
        private val externalUrl: String? =
            System.getProperty("it.datasource.url") ?: System.getenv("IT_DATASOURCE_URL")

        private val externalUsername: String =
            System.getProperty("it.datasource.username")
                ?: System.getenv("IT_DATASOURCE_USERNAME") ?: "starter"

        private val externalPassword: String =
            System.getProperty("it.datasource.password")
                ?: System.getenv("IT_DATASOURCE_PASSWORD") ?: "starter"

        // 외부 DB 가 지정되지 않은 경우에만 Testcontainers 컨테이너를 기동한다.
        private val postgres: PostgreSQLContainer<*>? =
            if (externalUrl == null) {
                PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("starter")
                    .withUsername("starter")
                    .withPassword("starter")
                    .also { it.start() }
            } else {
                null
            }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProps(registry: DynamicPropertyRegistry) {
            if (postgres != null) {
                registry.add("spring.datasource.url", postgres::getJdbcUrl)
                registry.add("spring.datasource.username", postgres::getUsername)
                registry.add("spring.datasource.password", postgres::getPassword)
            } else {
                registry.add("spring.datasource.url") { externalUrl }
                registry.add("spring.datasource.username") { externalUsername }
                registry.add("spring.datasource.password") { externalPassword }
            }
        }
    }
}
