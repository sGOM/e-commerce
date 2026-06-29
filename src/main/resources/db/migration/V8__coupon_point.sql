-- ── 쿠폰 / 포인트 (Phase 4) — 회원 전용 ──────────────────────────────────
-- 금액 계산: 상품합계 − 쿠폰할인 − 포인트사용 = 결제금액(전부 서버 계산).
-- 결제 실패/주문 취소 시 쿠폰은 미사용 복원, 사용 포인트는 환원.

-- 쿠폰 정의 (관리자/판매자가 발행)
CREATE TABLE coupons (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    discount_type       VARCHAR(20)  NOT NULL,            -- RATE(정률,%) / FIXED(정액,원)
    discount_value      BIGINT       NOT NULL,            -- RATE: 퍼센트, FIXED: 원
    min_order_amount    BIGINT       NOT NULL DEFAULT 0,  -- 최소 주문금액
    max_discount_amount BIGINT,                           -- 정률 할인 상한(nullable)
    valid_from          TIMESTAMPTZ  NOT NULL,
    valid_until         TIMESTAMPTZ  NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_coupon_value CHECK (discount_value >= 0 AND min_order_amount >= 0)
);

-- 회원에게 발급된 쿠폰 인스턴스(1회용)
CREATE TABLE issued_coupons (
    id         BIGSERIAL PRIMARY KEY,
    coupon_id  BIGINT      NOT NULL REFERENCES coupons (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    used       BOOLEAN     NOT NULL DEFAULT FALSE,
    used_at    TIMESTAMPTZ,
    order_id   BIGINT      REFERENCES orders (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_issued_coupons_user ON issued_coupons (user_id);

-- 회원 포인트 계좌(잔액 캐시) + 원장
CREATE TABLE point_accounts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    balance    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_point_balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE point_transactions (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES point_accounts (id) ON DELETE CASCADE,
    type       VARCHAR(20) NOT NULL,            -- EARN / USE / CANCEL_USE
    amount     BIGINT      NOT NULL,            -- 항상 양수(부호는 type 이 결정)
    order_id   BIGINT,                          -- 관련 주문(있으면)
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_point_txn_amount_positive CHECK (amount > 0)
);
CREATE INDEX idx_point_txn_account ON point_transactions (account_id);

-- 포인트 적립 정책(관리자 설정). 단일 행을 유지하며 적립률을 런타임 변경한다.
CREATE TABLE point_policies (
    id           BIGSERIAL PRIMARY KEY,
    earn_rate_bp INT         NOT NULL,          -- basis point (100 = 1%)
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_earn_rate_non_negative CHECK (earn_rate_bp >= 0)
);
INSERT INTO point_policies (earn_rate_bp, created_at, updated_at) VALUES (100, now(), now()); -- 기본 1%
