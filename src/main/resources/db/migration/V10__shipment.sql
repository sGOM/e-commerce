-- ── 배송 (Phase 5) — SubOrder(판매자) 단위 송장 ──────────────────────────
-- 한 주문에 여러 판매자가 섞이면 판매자별로 개별 발송한다. 판매자가 송장을 등록하면 SubOrder 가 SHIPPED 로 전이.

CREATE TABLE shipments (
    id              BIGSERIAL PRIMARY KEY,
    sub_order_id    BIGINT      NOT NULL UNIQUE REFERENCES sub_orders (id) ON DELETE CASCADE,
    courier         VARCHAR(50) NOT NULL,            -- 택배사
    tracking_number VARCHAR(100) NOT NULL,           -- 송장번호
    status          VARCHAR(20) NOT NULL,            -- SHIPPED / DELIVERED
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
