-- ── 마켓플레이스 카탈로그 (Phase 1) ──────────────────────────────────────
-- 판매자(입점 상점), 카테고리(계층형), 상품, 상품옵션(SKU), 재고

-- 판매자(입점 상점) — User(ROLE_SELLER) 1:1
CREATE TABLE sellers (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE REFERENCES users (id),
    store_name  VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_sellers_status ON sellers (status);

-- 카테고리 (계층형: parent_id 자기참조)
CREATE TABLE categories (
    id         BIGSERIAL PRIMARY KEY,
    parent_id  BIGINT REFERENCES categories (id),
    name       VARCHAR(100) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_categories_parent ON categories (parent_id);

-- 상품 — 반드시 한 판매자(Store)에 속한다
CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    seller_id   BIGINT       NOT NULL REFERENCES sellers (id),
    category_id BIGINT REFERENCES categories (id),
    name        VARCHAR(200) NOT NULL,
    description TEXT,
    base_price  BIGINT       NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_product_base_price CHECK (base_price >= 0)
);
CREATE INDEX idx_products_seller ON products (seller_id);
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status ON products (status);

-- 상품 옵션(SKU) — 실제 판매/재고의 단위
CREATE TABLE product_options (
    id               BIGSERIAL PRIMARY KEY,
    product_id       BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name             VARCHAR(200) NOT NULL,
    sku              VARCHAR(100) NOT NULL UNIQUE,
    additional_price BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_product_options_product ON product_options (product_id);

-- 재고 — 옵션 1:1. 재고 정합성의 단일 진실 공급원.
-- quantity = 총 보유, reserved = 주문 진행중 예약분. 가용 = quantity - reserved.
CREATE TABLE inventories (
    id         BIGSERIAL PRIMARY KEY,
    option_id  BIGINT      NOT NULL UNIQUE REFERENCES product_options (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL DEFAULT 0,
    reserved   INT         NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_inventory_non_negative CHECK (quantity >= 0 AND reserved >= 0 AND reserved <= quantity)
);

-- 판매자 역할 추가 (RBAC 확장)
INSERT INTO roles (name, description, created_at, updated_at) VALUES
    ('ROLE_SELLER', '입점 판매자', now(), now());
