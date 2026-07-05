-- ── 위시리스트(찜) + 가격 인하 알림 ──────────────────────────────────────────
-- `docs/planning/wishlist-price-alert.md`. 대상 단위는 Product(옵션 단위 아님, §4).
-- 회원 전용(게스트 미지원) — `carts`와 동일 원칙.

CREATE TABLE wishlists (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    product_id     BIGINT      NOT NULL REFERENCES products (id),
    -- 담을 당시 또는 마지막 알림 발송 시점의 Product.base_price 스냅샷(§4 baseline 정의)
    baseline_price BIGINT      NOT NULL,
    last_notified_at TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);
-- 같은 상품 중복 담기 방지(AC2)
CREATE UNIQUE INDEX uq_wishlists_user_product ON wishlists (user_id, product_id);
-- 가격 인하 이벤트 발생 시 해당 상품을 찜한 회원 조회(AC8)
CREATE INDEX idx_wishlists_product ON wishlists (product_id);
-- 마이페이지 "찜한 상품" 목록(최신순)
CREATE INDEX idx_wishlists_user ON wishlists (user_id);
