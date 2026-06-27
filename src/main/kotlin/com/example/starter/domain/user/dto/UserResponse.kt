package com.example.starter.domain.user.dto

import com.example.starter.domain.user.entity.User
import com.example.starter.domain.user.entity.UserStatus
import java.time.Instant

/**
 * 사용자 정보 응답.
 */
data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val status: UserStatus,
    val roles: List<String>,
    val createdAt: Instant,
) {
    companion object {
        fun from(user: User): UserResponse =
            UserResponse(
                id = requireNotNull(user.id),
                email = user.email,
                name = user.name,
                status = user.status,
                roles = user.roles.map { it.name }.sorted(),
                createdAt = user.createdAt,
            )
    }
}
