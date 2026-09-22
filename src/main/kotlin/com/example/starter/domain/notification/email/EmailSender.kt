package com.example.starter.domain.notification.email

/**
 * 이메일 발송기(ROADMAP 6.1). 구현은 SMTP 설정(`spring.mail.host`) 유무로 갈린다
 * ([EmailConfig]): 설정이 있으면 [SmtpEmailSender], 없으면 [LoggingEmailSender] 가 로그로 대신한다.
 */
interface EmailSender {
    fun send(to: String, subject: String, body: String)
}
