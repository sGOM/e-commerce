package com.example.starter.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 비동기 실행 설정. 감사 로그 저장/알림 발송에 사용하는 전용 스레드 풀을 제공한다.
 */
@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("auditExecutor")
    fun auditExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 500
            setThreadNamePrefix("audit-")
            // 큐 포화 시 호출 스레드에서 실행 → 감사 로그 유실 방지(요청은 잠깐 느려질 수 있음)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }

    /**
     * 재입고 알림 등 인앱 알림 생성에 사용하는 전용 스레드 풀. 인기 옵션 재입고 시 신청자가 많을 수
     * 있어(`docs/planning/restock-alert.md` "대량 발송 스로틀링") 감사 로그와 별도 풀로 분리해
     * 서로의 큐 포화가 영향을 주지 않게 한다.
     */
    @Bean("notificationExecutor")
    fun notificationExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            queueCapacity = 1000
            setThreadNamePrefix("notification-")
            // 큐 포화 시 호출 스레드에서 실행 — 알림 유실보다 잠깐의 지연이 낫다
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            initialize()
        }
}
