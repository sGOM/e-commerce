package com.example.starter.common.config

import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Kotlin JDSL 설정.
 *
 * [JpqlRenderContext] 빈을 등록하면 spring-data-jpa-support 모듈이
 * `KotlinJdslJpqlExecutor` 를 리포지토리에 주입한다. 각 리포지토리는 해당 인터페이스를
 * 상속하여 타입 안전한 동적 쿼리를 작성한다.
 */
@Configuration
class JdslConfig {

    @Bean
    fun jpqlRenderContext(): JpqlRenderContext = JpqlRenderContext()
}
