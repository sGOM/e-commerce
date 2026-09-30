package com.example.starter.domain.notification.push

import com.example.starter.common.exception.BusinessException
import com.example.starter.common.exception.ErrorCode
import com.example.starter.domain.notification.dto.PushPublicKeyResponse
import com.example.starter.domain.notification.dto.SubscribePushRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.Base64

/** 웹 푸시 구독 등록(ROADMAP 6.3). 브라우저가 준 구독을 검증해 회원에게 붙인다. */
@Service
@Transactional(readOnly = true)
class PushSubscriptionService(
    private val pushSubscriptionRepository: PushSubscriptionRepository,
    private val vapidProperties: VapidProperties,
) {

    fun publicKey(): PushPublicKeyResponse =
        PushPublicKeyResponse(vapidProperties.publicKey.takeIf { vapidProperties.enabled })

    @Transactional
    fun subscribe(userId: Long, request: SubscribePushRequest) {
        if (!vapidProperties.enabled) throw BusinessException(ErrorCode.PUSH_NOT_CONFIGURED)
        val endpoint = request.endpoint!!
        val p256dh = request.keys!!.p256dh!!
        val auth = request.keys.auth!!
        validate(endpoint, p256dh, auth)

        val existing = pushSubscriptionRepository.findByEndpoint(endpoint)
        if (existing != null) {
            existing.renew(userId, p256dh, auth)
        } else {
            pushSubscriptionRepository.save(PushSubscription(userId = userId, endpoint = endpoint, p256dh = p256dh, auth = auth))
        }
    }

    /**
     * 서버가 이 URL 로 직접 POST 하므로(SSRF 위험) 알려진 브라우저 푸시 서비스의 https 주소만 받는다.
     * 키는 암호화 단계에서 깨지지 않게 형식(P-256 비압축 65바이트, auth 16바이트)까지 확인한다.
     */
    private fun validate(endpoint: String, p256dh: String, auth: String) {
        val uri = runCatching { URI(endpoint) }.getOrNull()
        val host = uri?.host?.lowercase()
        val knownHost = host != null && PUSH_SERVICE_HOSTS.any { host == it || host.endsWith(".$it") }
        val keysValid = runCatching {
            WebPushCrypto.publicKey(Base64.getUrlDecoder().decode(p256dh))
            Base64.getUrlDecoder().decode(auth).size == 16
        }.getOrDefault(false)
        if (uri?.scheme != "https" || !knownHost || !keysValid) {
            throw BusinessException(ErrorCode.PUSH_INVALID_SUBSCRIPTION)
        }
    }

    companion object {
        /** Chrome(FCM)·Firefox(Mozilla autopush)·Edge(WNS)·Safari(APNs) 푸시 서비스. 새 브라우저가 생기면 추가한다. */
        private val PUSH_SERVICE_HOSTS = listOf(
            "fcm.googleapis.com",
            "push.services.mozilla.com",
            "notify.windows.com",
            "push.apple.com",
        )
    }
}
