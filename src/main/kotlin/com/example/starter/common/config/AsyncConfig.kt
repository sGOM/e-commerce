package com.example.starter.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

/**
 * 비동기 실행 설정. 감사 로그 저장에 사용하는 전용 스레드 풀을 제공한다.
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
}
