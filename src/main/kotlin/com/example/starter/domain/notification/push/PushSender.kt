package com.example.starter.domain.notification.push

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.security.KeyPair
import java.time.Instant
import java.util.Base64

/**
 * 웹 푸시 발송기(ROADMAP 6.3). 구현은 VAPID 키(`push.vapid.private-key`) 유무로 갈린다([PushConfig]):
 * 있으면 [WebPushSender], 없으면 [LoggingPushSender] 가 로그로 대신한다.
 */
interface PushSender {
    /** [PushResult.GONE] 은 푸시 서비스가 구독 만료(404/410)를 알린 것 — 호출자가 구독을 지운다. */
    fun send(subscription: PushSubscription, payload: String): PushResult
}

enum class PushResult { SENT, GONE }

/**
 * VAPID 키 쌍(base64url 원시값 — `npx web-push generate-vapid-keys` 출력 형식)과 연락처.
 * 공개키는 프론트가 구독할 때 applicationServerKey 로 쓴다.
 */
@ConfigurationProperties(prefix = "push.vapid")
data class VapidProperties(
    val publicKey: String = "",
    val privateKey: String = "",
    val subject: String = "mailto:no-reply@example.com",
) {
    val enabled: Boolean get() = publicKey.isNotBlank() && privateKey.isNotBlank()
}

@Configuration
class PushConfig {

    @Bean
    @ConditionalOnProperty("push.vapid.private-key")
    fun webPushSender(properties: VapidProperties): PushSender = WebPushSender(properties)

    @Bean
    @ConditionalOnProperty(name = ["push.vapid.private-key"], havingValue = "__never__", matchIfMissing = true)
    fun loggingPushSender(): PushSender = LoggingPushSender()
}

/** RFC 8030 푸시 서비스로 암호화(RFC 8291)·VAPID(RFC 8292) 서명한 메시지를 POST 한다. */
class WebPushSender(
    private val properties: VapidProperties,
    private val restClient: RestClient = RestClient.create(),
) : PushSender {

    private val b64 = Base64.getUrlDecoder()
    private val vapidKeys: KeyPair = WebPushCrypto.keyPair(b64.decode(properties.publicKey), b64.decode(properties.privateKey))

    override fun send(subscription: PushSubscription, payload: String): PushResult {
        val body = WebPushCrypto.encrypt(payload.toByteArray(), b64.decode(subscription.p256dh), b64.decode(subscription.auth))
        return try {
            restClient.post()
                .uri(subscription.endpoint)
                .header("TTL", TTL_SECONDS.toString())
                .header(HttpHeaders.CONTENT_ENCODING, "aes128gcm")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    WebPushCrypto.vapidAuthorization(subscription.endpoint, vapidKeys, properties.subject, Instant.now().epochSecond),
                )
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body)
                .retrieve()
                .toBodilessEntity()
            PushResult.SENT
        } catch (e: HttpClientErrorException) {
            // RFC 8030 §7.3: 만료·해지된 구독은 404/410
            if (e.statusCode == HttpStatus.NOT_FOUND || e.statusCode == HttpStatus.GONE) PushResult.GONE else throw e
        }
    }

    companion object {
        /** 기기가 꺼져 있을 때 푸시 서비스가 보관하는 시간 — 알림은 하루 지나면 의미가 옅다. */
        private const val TTL_SECONDS = 86_400
    }
}

/** VAPID 미설정 환경용 — 발송 대신 로그로 남긴다. */
class LoggingPushSender : PushSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(subscription: PushSubscription, payload: String): PushResult {
        log.info("[PUSH:미발송] userId={} payload={}", subscription.userId, payload)
        return PushResult.SENT
    }
}
