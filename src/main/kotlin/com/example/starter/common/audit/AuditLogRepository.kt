package com.example.starter.common.audit

import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 관리자 화면의 동적 검색을 위해 [KotlinJdslJpqlExecutor] 를 상속한다.
 */
interface AuditLogRepository : JpaRepository<AuditLog, Long>, KotlinJdslJpqlExecutor
