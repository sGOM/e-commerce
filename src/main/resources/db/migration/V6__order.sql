-- ── 주문 (Phase 3) — 멀티셀러 SubOrder 분리 ───────────────────────────────
-- 결제는 Order 단위 1건(Phase 4), 배송·취소·정산은 SubOrder(판매자) 단위.
-- 금액은 모두 서버 계산. 주문 항목은 생성 시점 가격/상품명/옵션을 스냅샷으로 보관한다.

CREATE TABLE orders (
    id              BIGSERIAL PRIMARY KEY,
    order_number    VARCHAR(40)  NOT NULL UNIQUE,         -- 게스트 조회/멱등 키
    user_id         BIGINT       REFERENCES users (id) ON DELETE SET NULL, -- 게스트는 NULL
    orderer_name    VARCHAR(100) NOT NULL,
    orderer_phone   VARCHAR(30)  NOT NULL,
    orderer_email   VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL,                -- CREATED / PAID / CANCELED
    total_amount    BIGINT       NOT NULL,                -- 상품 합계
    discount_amount BIGINT       NOT NULL DEFAULT 0,      -- 쿠폰 할인 (Phase 4)
    point_used      BIGINT       NOT NULL DEFAULT 0,      -- 포인트 사용 (Phase 4)
    payable_amount  BIGINT       NOT NULL,                -- 최종 결제금액 = total - discount - point
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_order_amount_non_negative
        CHECK (total_amount >= 0 AND discount_amount >= 0 AND point_used >= 0 AND payable_amount >= 0)
);
CREATE INDEX idx_orders_user ON orders (user_id);

CREATE TABLE sub_orders (
    id         BIGSERIAL PRIMARY KEY,
    order_id   BIGINT      NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    seller_id  BIGINT      NOT NULL REFERENCES sellers (id),
    status     VARCHAR(20) NOT NULL,    -- CREATED / PAID / PREPARING / SHIPPED / DELIVERED / CANCELED
    subtotal   BIGINT      NOT NULL,    -- 해당 판매자 상품 합계
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_sub_order_subtotal_non_negative CHECK (subtotal >= 0)
);
CREATE INDEX idx_sub_orders_order ON sub_orders (order_id);
CREATE INDEX idx_sub_orders_seller ON sub_orders (seller_id);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    sub_order_id BIGINT       NOT NULL REFERENCES sub_orders (id) ON DELETE CASCADE,
    option_id    BIGINT       NOT NULL REFERENCES product_options (id), -- 참조용(본체는 스냅샷)
    product_name VARCHAR(200) NOT NULL,  -- 주문 시점 스냅샷
    option_name  VARCHAR(200) NOT NULL,  -- 주문 시점 스냅샷
    unit_price   BIGINT       NOT NULL,  -- 주문 시점 단가(기본가 + 옵션추가금)
    quantity     INT          NOT NULL,
    line_total   BIGINT       NOT NULL,  -- unit_price * quantity
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_order_item_qty CHECK (quantity > 0)
);
CREATE INDEX idx_order_items_sub_order ON order_items (sub_order_id);
