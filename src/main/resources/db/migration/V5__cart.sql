-- ── 장바구니 (Phase 2) ───────────────────────────────────────────────────
-- 회원당 1개 장바구니. 게스트는 장바구니 미지원(즉시 주문).

CREATE TABLE carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT      NOT NULL REFERENCES carts (id) ON DELETE CASCADE,
    option_id  BIGINT      NOT NULL REFERENCES product_options (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_cart_option UNIQUE (cart_id, option_id),
    CONSTRAINT chk_cart_item_qty CHECK (quantity > 0)
);
CREATE INDEX idx_cart_items_cart ON cart_items (cart_id);
