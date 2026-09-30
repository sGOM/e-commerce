package com.example.starter.domain.notification.push

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.URI
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 웹 푸시 메시지 암호화([RFC 8291](https://www.rfc-editor.org/rfc/rfc8291), aes128gcm)와
 * VAPID 인증 헤더([RFC 8292](https://www.rfc-editor.org/rfc/rfc8292)). JDK 표준 암호 API(ECDH·HMAC·AES-GCM·ECDSA)만 쓴다 —
 * 공개 라이브러리(web-push-java)는 BouncyCastle 의존과 유지보수 중단 문제가 있어 두 RFC 를 직접 구현했고,
 * 정확성은 RFC 8291 부록 A 예제로 바이트 단위 검증한다(WebPushCryptoTest).
 */
object WebPushCrypto {

    private const val RECORD_SIZE = 4096
    private val b64url = Base64.getUrlEncoder().withoutPadding()
    private val random = SecureRandom()

    /** P-256 곡선 파라미터(원시 키를 JCA 키로 바꿀 때 쓴다). */
    private val p256: ECParameterSpec by lazy { (generateKeyPair().public as ECPublicKey).params }

    fun generateKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    /** 비압축 점(0x04||X||Y, 65바이트)과 개인키 스칼라(32바이트) 원시값 — VAPID 키·브라우저 p256dh 형식. */
    fun keyPair(publicRaw: ByteArray, privateRaw: ByteArray): KeyPair {
        val factory = KeyFactory.getInstance("EC")
        return KeyPair(
            publicKey(publicRaw),
            factory.generatePrivate(ECPrivateKeySpec(BigInteger(1, privateRaw), p256)),
        )
    }

    fun publicKey(raw: ByteArray): PublicKey {
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "비압축 P-256 공개키(65바이트)가 아닙니다" }
        val point = ECPoint(BigInteger(1, raw.copyOfRange(1, 33)), BigInteger(1, raw.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, p256))
    }

    fun rawPublic(key: PublicKey): ByteArray {
        val w = (key as ECPublicKey).w
        return byteArrayOf(0x04) + fixed32(w.affineX) + fixed32(w.affineY)
    }

    /**
     * 단일 레코드 aes128gcm 본문 = 헤더(salt 16 || rs 4 || idlen 1 || keyid=as_public 65) || 암호문+태그.
     * [salt]·[senderKeys] 는 테스트 벡터 재현용이며 운영에선 매 메시지 새로 만든다.
     */
    fun encrypt(
        plaintext: ByteArray,
        uaPublic: ByteArray,
        authSecret: ByteArray,
        salt: ByteArray = ByteArray(16).also(random::nextBytes),
        senderKeys: KeyPair = generateKeyPair(),
    ): ByteArray {
        require(plaintext.size + 1 + 16 <= RECORD_SIZE) { "푸시 페이로드가 너무 큽니다" }
        val asPublic = rawPublic(senderKeys.public)
        val ecdhSecret = KeyAgreement.getInstance("ECDH").run {
            init(senderKeys.private)
            doPhase(publicKey(uaPublic), true)
            generateSecret()
        }
        // RFC 8291 §3.4: auth_secret 로 ECDH 비밀을 섞어 IKM 을 만들고, RFC 8188 로 CEK·NONCE 를 뽑는다
        val prkKey = hmac(authSecret, ecdhSecret)
        val ikm = hmac(prkKey, "WebPush: info".toByteArray() + 0 + uaPublic + asPublic + 1)
        val prk = hmac(salt, ikm)
        val cek = hmac(prk, "Content-Encoding: aes128gcm".toByteArray() + 0 + 1).copyOf(16)
        val nonce = hmac(prk, "Content-Encoding: nonce".toByteArray() + 0 + 1).copyOf(12)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
        }
        val ciphertext = cipher.doFinal(plaintext + 2) // 0x02 = 마지막 레코드 구분자(패딩 없음)

        return ByteArrayOutputStream().apply {
            write(salt)
            write(ByteBuffer.allocate(4).putInt(RECORD_SIZE).array())
            write(asPublic.size)
            write(asPublic)
            write(ciphertext)
        }.toByteArray()
    }

    /** `Authorization: vapid t=<JWT>, k=<공개키>` — aud 는 푸시 서비스 origin, 만료는 12시간(RFC 8292 §2 상한 24시간). */
    fun vapidAuthorization(endpoint: String, vapidKeys: KeyPair, subject: String, nowEpochSeconds: Long): String {
        val uri = URI(endpoint)
        val audience = "${uri.scheme}://${uri.authority}"
        val header = b64url.encodeToString("""{"typ":"JWT","alg":"ES256"}""".toByteArray())
        val claims = b64url.encodeToString(
            """{"aud":"$audience","exp":${nowEpochSeconds + 12 * 3600},"sub":"$subject"}""".toByteArray(),
        )
        // JWS ES256 서명은 DER 이 아니라 R||S 64바이트 — JDK 의 P1363 형식이 그대로 맞는다
        val signature = Signature.getInstance("SHA256withECDSAinP1363Format").run {
            initSign(vapidKeys.private)
            update("$header.$claims".toByteArray())
            sign()
        }
        return "vapid t=$header.$claims.${b64url.encodeToString(signature)}, k=${b64url.encodeToString(rawPublic(vapidKeys.public))}"
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    private operator fun ByteArray.plus(b: Int): ByteArray = this + b.toByte()

    private fun fixed32(n: BigInteger): ByteArray {
        val bytes = n.toByteArray()
        return when {
            bytes.size == 32 -> bytes
            bytes.size > 32 -> bytes.copyOfRange(bytes.size - 32, bytes.size) // 부호 바이트 제거
            else -> ByteArray(32 - bytes.size) + bytes
        }
    }
}
