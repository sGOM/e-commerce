package com.example.starter.domain.notification.email

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

/**
 * SMTP 가 설정된 환경에서만 실제 발송기를 등록한다. 로컬·테스트처럼 `spring.mail.host` 가 없으면
 * 로깅 발송기가 대신 들어가 알림 흐름과 비밀번호 재설정 토큰을 로그로 확인할 수 있다.
 */
@Configuration
class EmailConfig {

    @Bean
    @ConditionalOnProperty("spring.mail.host")
    fun smtpEmailSender(
        mailSender: JavaMailSender,
        @org.springframework.beans.factory.annotation.Value("\${notification.email.from:no-reply@example.com}") from: String,
    ): EmailSender = SmtpEmailSender(mailSender, from)

    @Bean
    @ConditionalOnProperty(name = ["spring.mail.host"], havingValue = "__never__", matchIfMissing = true)
    fun loggingEmailSender(): EmailSender = LoggingEmailSender()
}

class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    private val from: String,
) : EmailSender {

    override fun send(to: String, subject: String, body: String) {
        mailSender.send(
            SimpleMailMessage().apply {
                setFrom(from)
                setTo(to)
                setSubject(subject)
                setText(body)
            },
        )
    }
}

/** SMTP 미설정 환경용 — 발송 대신 로그로 남긴다. */
class LoggingEmailSender : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(to: String, subject: String, body: String) {
        log.info("[EMAIL:미발송] to={} subject={} body={}", to, subject, body)
    }
}
