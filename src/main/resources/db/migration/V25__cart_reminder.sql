-- ── 장바구니 이탈 리마인드 ────────────────────────────────────────────────
-- MVP는 리마인드 알림만(자동 쿠폰 발급 없음, 어뷰징 회피). 카트 이탈 판단에 필요한 마지막 활동
-- 시각/마지막 리마인드 시각 컬럼을 추가한다. 항목(cart_items) 변경은 부모(carts) 엔티티의
-- BaseTimeEntity.updatedAt 을 자동으로 갱신하지 않으므로(자식 컬렉션 변경은 부모를 dirty 처리하지
-- 않음) 별도 컬럼으로 명시적으로 관리한다(CartService 가 항목 변경 시마다 touch()).

ALTER TABLE carts ADD COLUMN last_activity_at TIMESTAMPTZ;
ALTER TABLE carts ADD COLUMN last_reminder_at TIMESTAMPTZ;

-- 기존 행은 updated_at 을 초기값으로 채운다.
UPDATE carts SET last_activity_at = updated_at WHERE last_activity_at IS NULL;
ALTER TABLE carts ALTER COLUMN last_activity_at SET NOT NULL;

-- 배치 스캔("마지막 활동 후 N시간 경과했으나 미결제") 조건 조회에 사용
CREATE INDEX idx_carts_last_activity_at ON carts (last_activity_at);
