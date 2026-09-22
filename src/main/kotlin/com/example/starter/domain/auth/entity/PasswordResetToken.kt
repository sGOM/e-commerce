package com.example.starter.domain.auth.entity

import com.example.starter.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * 비밀번호 재설정 토큰. 메일로 보낸 원문 토큰은 저장하지 않고 [tokenHash](SHA-256)만 보관해
 * DB 가 유출돼도 재설정 링크를 만들 수 없게 한다. 30분 만료([expiresAt]) + 1회용([usedAt]).
 */
@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    fun isUsable(now: Instant): Boolean = usedAt == null && expiresAt.isAfter(now)

    fun markUsed(now: Instant) {
        usedAt = now
    }
}
