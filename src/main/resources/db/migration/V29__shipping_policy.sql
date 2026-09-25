-- 기본 배송비 정책(ROADMAP 7.1, 2026-09-25 확정: 3,000원). 단일 행, 관리자가 런타임 변경한다.
-- 판매자(SubOrder) 단위로 부과되며, 멤버십 무료배송 혜택이 활성인 회원은 면제된다.
CREATE TABLE shipping_policies (
    id         BIGSERIAL PRIMARY KEY,
    base_fee   BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_shipping_base_fee_non_negative CHECK (base_fee >= 0)
);
INSERT INTO shipping_policies (base_fee, created_at, updated_at) VALUES (3000, now(), now());
