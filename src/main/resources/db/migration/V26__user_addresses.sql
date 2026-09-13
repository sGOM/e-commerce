-- ── 배송지 주소록 ──────────────────────────────────────────
-- 회원 전용. 주문 시 선택한 주소는 orders 의 배송지 컬럼으로 복사(스냅샷)되므로,
-- 주소록을 수정·삭제해도 과거 주문의 배송지는 바뀌지 않는다.

CREATE TABLE user_addresses (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    label          VARCHAR(50),
    receiver_name  VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(30)  NOT NULL,
    zipcode        VARCHAR(10)  NOT NULL,
    address1       VARCHAR(255) NOT NULL,
    address2       VARCHAR(255),
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_user_addresses_user ON user_addresses (user_id);
-- 회원당 기본 배송지는 최대 1개 — 앱 로직이 어긋나도 DB 가 막는다.
CREATE UNIQUE INDEX uq_user_addresses_default ON user_addresses (user_id) WHERE is_default;
