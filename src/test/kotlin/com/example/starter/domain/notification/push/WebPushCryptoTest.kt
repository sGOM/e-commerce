package com.example.starter.domain.notification.push

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.Signature
import java.util.Base64

/** RFC 8291 §5·부록 A 의 공식 예제 값으로 암호화 결과를 바이트 단위까지 검증하고, RFC 8292 VAPID 헤더를 서명 검증한다. */
class WebPushCryptoTest {

    private val b64 = Base64.getUrlDecoder()

    @Test
    fun `RFC 8291 예제와 같은 암호문을 만든다`() {
        val body = WebPushCrypto.encrypt(
            plaintext = "When I grow up, I want to be a watermelon".toByteArray(),
            uaPublic = b64.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
            authSecret = b64.decode("BTBZMqHH6r4Tts7J_aSIgg"),
            salt = b64.decode("DGv6ra1nlYgDCS1FRnbzlw"),
            senderKeys = WebPushCrypto.keyPair(
                publicRaw = b64.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"),
                privateRaw = b64.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"),
            ),
        )

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml" +
                "mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT" +
                "pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN",
        )
    }

    @Test
    fun `VAPID 헤더는 푸시 서비스 origin 을 aud 로 서명한 ES256 JWT 와 공개키를 담는다`() {
        val keys = WebPushCrypto.generateKeyPair()
        val publicRaw = WebPushCrypto.rawPublic(keys.public)

        val header = WebPushCrypto.vapidAuthorization(
            endpoint = "https://fcm.googleapis.com/fcm/send/abc",
            vapidKeys = keys,
            subject = "mailto:ops@example.com",
            nowEpochSeconds = 1_700_000_000,
        )

        val (t, k) = Regex("""^vapid t=([^,]+), k=(.+)$""").find(header)!!.destructured
        assertThat(b64.decode(k)).isEqualTo(publicRaw)
        val (h, p, s) = t.split(".")
        val claims = String(b64.decode(p))
        assertThat(String(b64.decode(h))).contains("\"alg\":\"ES256\"")
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"", "\"exp\":1700043200", "\"sub\":\"mailto:ops@example.com\"")
        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initVerify(keys.public)
            update("$h.$p".toByteArray())
        }
        assertThat(verifier.verify(b64.decode(s))).isTrue()
    }
}
