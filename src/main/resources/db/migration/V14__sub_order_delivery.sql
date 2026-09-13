-- ── 구매확정(수령확인) — SHIPPED → DELIVERED ────────────────────────────
-- 지금까지는 SubOrder 를 DELIVERED 로 전이시키는 주체가 없었다(송장 등록만 SHIPPED 까지 커버).
-- 리뷰(product-reviews 기획)는 "배송완료" 를 작성 자격 기준으로 삼으므로, 구매자가 수령을 확인하는
-- 명시적 전이를 추가한다. delivered_at 은 전이 시점을 정확히 기록해 리뷰 작성 가능 기간(N일) 계산에 쓴다.

ALTER TABLE sub_orders
    ADD COLUMN delivered_at TIMESTAMPTZ;
