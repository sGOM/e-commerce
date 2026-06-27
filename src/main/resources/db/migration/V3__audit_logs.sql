-- 요청 단위 감사 로그 (append-only)
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,
    method      VARCHAR(10)   NOT NULL,
    uri         VARCHAR(2048) NOT NULL,
    ip          VARCHAR(45),
    user_agent  VARCHAR(512),
    status_code INTEGER       NOT NULL,
    duration_ms BIGINT        NOT NULL,
    payload     JSONB,
    created_at  TIMESTAMPTZ   NOT NULL
);

-- 조회 패턴: 사용자별 / 시간순 / 상태코드별, 그리고 payload JSONB 검색
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at DESC);
CREATE INDEX idx_audit_logs_status_code ON audit_logs (status_code);
CREATE INDEX idx_audit_logs_payload ON audit_logs USING GIN (payload);
