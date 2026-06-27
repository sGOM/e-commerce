package com.example.starter.domain.user.repository

import com.example.starter.domain.user.entity.User
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

/**
 * [KotlinJdslJpqlExecutor] 를 상속하여 관리자 검색 등 동적 쿼리를 타입 안전하게 작성한다.
 */
interface UserRepository : JpaRepository<User, Long>, KotlinJdslJpqlExecutor {

    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    /** 로그인용 — 역할과 권한까지 한 번에 로딩 (N+1 방지) */
    @EntityGraph(attributePaths = ["roles", "roles.permissions"])
    fun findWithRolesByEmail(email: String): User?
}
