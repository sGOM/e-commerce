-- 알림 수신 설정(2026-10-01): 메일·푸시로 나가는 알림을 종류×채널별로 끈다. 행이 없으면 받는다(기본 켜짐 —
-- 설정 도입 전 동작 유지). 인앱 알림함은 항상 받으므로 대상이 아니다.
CREATE TABLE notification_preferences (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    type       VARCHAR(30) NOT NULL,
    channel    VARCHAR(10) NOT NULL, -- EMAIL / PUSH
    enabled    BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notification_preferences UNIQUE (user_id, type, channel)
);
