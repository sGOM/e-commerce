-- ── 포인트 만료 (lot 기반 FIFO) ──────────────────────────────────────────
-- 적립마다 만료일을 가진 lot 을 만들고, 사용 시 만료 임박 순으로 차감한다.
-- 만료 처리는 lot 의 잔여분만 소멸시켜 이중 차감을 막는다. 유효기간은 관리자 설정값.

ALTER TABLE point_policies
    ADD COLUMN expiry_days INT NOT NULL DEFAULT 365; -- 적립 유효기간(일)

CREATE TABLE point_lots (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT      NOT NULL REFERENCES point_accounts (id) ON DELETE CASCADE,
    amount          BIGINT      NOT NULL,            -- 최초 적립액
    remaining       BIGINT      NOT NULL,            -- 남은 잔여(사용/만료/회수로 감소)
    expires_at      TIMESTAMPTZ NOT NULL,
    source_order_id BIGINT,                          -- 적립 출처 주문(있으면)
    status          VARCHAR(20) NOT NULL,            -- ACTIVE / EXHAUSTED / EXPIRED
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_point_lot_amount CHECK (amount > 0 AND remaining >= 0 AND remaining <= amount)
);
CREATE INDEX idx_point_lots_account ON point_lots (account_id);
CREATE INDEX idx_point_lots_expiry ON point_lots (status, expires_at);
