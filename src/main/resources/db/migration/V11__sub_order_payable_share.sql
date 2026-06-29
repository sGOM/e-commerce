-- ── SubOrder 부분 취소/환불 지원 ─────────────────────────────────────────
-- 각 하위 주문의 실제 결제 기여액(쿠폰/포인트 차감 반영)을 미리 배분해 저장한다.
-- 부분 취소 시 이 금액만큼만 환불 처리하며, 모든 SubOrder 합 = orders.payable_amount.

ALTER TABLE sub_orders
    ADD COLUMN payable_share BIGINT NOT NULL DEFAULT 0;
