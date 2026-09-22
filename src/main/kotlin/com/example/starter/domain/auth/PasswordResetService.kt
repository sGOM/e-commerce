package com.example.starter.domain.auth

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.auth.entity.PasswordResetToken
import com.example.starter.domain.auth.repository.PasswordResetTokenRepository
import com.example.starter.domain.notification.email.EmailSender
import com.example.starter.domain.user.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * 비밀번호 분실 재설정(ROADMAP 3.1). 메일로 보낸 토큰으로만 비밀번호를 바꾼다.
 *
 * 요청 응답은 계정 존재 여부와 무관하게 항상 성공이다(계정 열거 방지) — 가입되지 않은 주소면 메일만 보내지 않는다.
 */
@Service
@Transactional(readOnly = true)
class PasswordResetService(
    private val userRepository: UserRepository,
    private val tokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailSender: EmailSender,
    @Value("\${app.base-url:http://localhost:5173}") private val baseUrl: String,
) {

    @Transactional
    fun request(email: String) {
        val user = userRepository.findByEmail(email.trim()) ?: return
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        tokenRepository.save(
            PasswordResetToken(
                userId = requireNotNull(user.id),
                tokenHash = hash(token),
                expiresAt = Instant.now().plus(TTL),
            ),
        )
        val link = "$baseUrl/reset-password?token=$token"
        emailSender.send(
            user.email,
            "비밀번호 재설정 안내",
            "아래 링크에서 새 비밀번호를 설정하세요(${TTL.toMinutes()}분 후 만료, 1회만 사용 가능).\n$link",
        )
    }

    @Transactional
    fun confirm(token: String, newPassword: String) {
        val now = Instant.now()
        val found = tokenRepository.findByTokenHash(hash(token))
        if (found == null || !found.isUsable(now)) {
            throw BusinessException(ErrorCode.INVALID_RESET_TOKEN)
        }
        val user = userRepository.findById(found.userId)
            .orElseThrow { BusinessException(ErrorCode.INVALID_RESET_TOKEN) }
        user.password = passwordEncoder.encode(newPassword)
        // 재설정한 순간 같은 회원의 남은 링크는 모두 무효화한다.
        tokenRepository.findByUserIdAndUsedAtIsNull(found.userId).forEach { it.markUsed(now) }
        log.info("비밀번호 재설정 완료 userId={}", found.userId)
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val log = LoggerFactory.getLogger(PasswordResetService::class.java)
        private val TTL: Duration = Duration.ofMinutes(30)
        private val random = SecureRandom()
    }
}
