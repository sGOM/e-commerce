-- 회원 탈퇴(ROADMAP 3.3, 2026-09-25 확정: soft delete). 행과 주문·정산 이력은 남기고 상태(WITHDRAWN)와 시각만 기록한다.
ALTER TABLE users ADD COLUMN withdrawn_at TIMESTAMPTZ;
