-- ── 셀러 정산 (마켓플레이스) ──────────────────────────────────────────────
-- 미정산 SubOrder(취소 제외)를 판매자별로 모아 정산서를 만든다.
-- 지급액 = 판매액(∑subtotal) − 플랫폼 수수료(관리자 설정 수수료율). 정산 단위는 판매자.

-- 플랫폼 수수료 정책(관리자 설정, 단일 행). basis point(1000 = 10%).
CREATE TABLE settlement_policies (
    id                 BIGSERIAL PRIMARY KEY,
    commission_rate_bp INT         NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_commission_rate CHECK (commission_rate_bp >= 0 AND commission_rate_bp <= 10000)
);
INSERT INTO settlement_policies (commission_rate_bp, created_at, updated_at) VALUES (1000, now(), now()); -- 기본 10%

CREATE TABLE settlements (
    id                BIGSERIAL PRIMARY KEY,
    seller_id         BIGINT      NOT NULL REFERENCES sellers (id),
    sales_amount      BIGINT      NOT NULL,          -- 판매액 합(∑subtotal)
    commission_amount BIGINT      NOT NULL,          -- 플랫폼 수수료
    payout_amount     BIGINT      NOT NULL,          -- 지급액 = sales − commission
    settled_count     INT         NOT NULL,          -- 포함된 SubOrder 수
    status            VARCHAR(20) NOT NULL,          -- PENDING / PAID
    paid_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_settlement_amount CHECK (sales_amount >= 0 AND commission_amount >= 0 AND payout_amount >= 0)
);
CREATE INDEX idx_settlements_seller ON settlements (seller_id);

-- 정산에 포함된 SubOrder 표시(NULL = 미정산)
ALTER TABLE sub_orders
    ADD COLUMN settlement_id BIGINT REFERENCES settlements (id);
CREATE INDEX idx_sub_orders_settlement ON sub_orders (settlement_id);
