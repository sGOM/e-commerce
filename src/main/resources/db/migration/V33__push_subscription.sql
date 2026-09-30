-- 웹 푸시 구독(ROADMAP 6.3). 브라우저 PushSubscription 1개 = 1행. 같은 브라우저를 다른 계정이 다시 구독하면
-- endpoint 가 같으므로 행을 새 회원으로 옮긴다(유니크). 푸시 서비스가 404/410 을 주면 발송 중에 삭제된다.
CREATE TABLE push_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    endpoint   VARCHAR(1000) NOT NULL,
    p256dh     VARCHAR(100)  NOT NULL, -- 브라우저 ECDH 공개키(base64url, 비압축 P-256)
    auth       VARCHAR(50)   NOT NULL, -- 인증 비밀 16바이트(base64url)
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_push_subscriptions_endpoint UNIQUE (endpoint)
);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
