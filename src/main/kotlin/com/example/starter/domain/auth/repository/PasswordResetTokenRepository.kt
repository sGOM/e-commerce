package com.example.starter.domain.auth.repository

import com.example.starter.domain.auth.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {

    fun findByTokenHash(tokenHash: String): PasswordResetToken?

    /** 재설정 완료 시 같은 회원의 남은 토큰을 모두 무효화하기 위해 조회한다. */
    fun findByUserIdAndUsedAtIsNull(userId: Long): List<PasswordResetToken>
}
