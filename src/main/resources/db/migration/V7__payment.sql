-- ── 결제 (Phase 4) — Order 단위 1건 + 상태 이력 ──────────────────────────
-- MVP 는 Mock PG 어댑터(동기 승인). 멱등성은 order_id UNIQUE 로 주문당 결제 1건을 보장한다.
-- 금액은 주문의 payable_amount(서버 계산) 를 그대로 사용한다.

CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT      NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    status            VARCHAR(20) NOT NULL,            -- READY / PAID / FAILED / CANCELED
    amount            BIGINT      NOT NULL,
    method            VARCHAR(20) NOT NULL,            -- MOCK
    pg_transaction_id VARCHAR(100),                    -- PG 거래번호(승인 시)
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_payment_amount_non_negative CHECK (amount >= 0)
);

CREATE TABLE payment_events (
    id         BIGSERIAL PRIMARY KEY,
    payment_id BIGINT      NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    status     VARCHAR(20) NOT NULL,                   -- 전이된 상태
    detail     VARCHAR(500),                           -- PG 응답/사유
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_payment_events_payment ON payment_events (payment_id);
