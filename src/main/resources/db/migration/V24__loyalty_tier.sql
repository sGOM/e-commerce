-- ── 누적구매 등급(로열티 티어) ─────────────────────────────────────────────
-- 최근 12개월 순구매액(실결제액, 취소/환불 제외) 기준 BRONZE/SILVER/GOLD/VIP 4단계.
-- 배치(LoyaltyTierBatchService)가 회원별로 재계산해 이 테이블 1행씩을 갱신한다(강등 포함).
-- 유료 구독형 Membership(포인트 적립 배수)과는 별개 개념 — 혜택은 등급 전용 쿠폰/무료배송 등으로
-- 차별화한다(포인트 배수 충돌 회피, spring-expert 결정).

CREATE TABLE loyalty_tier_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id),
    tier                VARCHAR(20) NOT NULL DEFAULT 'BRONZE',
    -- 최근 12개월(롤링 윈도우) 순구매액 스냅샷(원). 배치 재계산 시마다 갱신.
    net_purchase_amount BIGINT      NOT NULL DEFAULT 0,
    calculated_at       TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_loyalty_tier_profiles_user ON loyalty_tier_profiles (user_id);
-- 관리자 등급별 필터 조회
CREATE INDEX idx_loyalty_tier_profiles_tier ON loyalty_tier_profiles (tier);
