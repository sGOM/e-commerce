-- 멤버십 전용 쿠폰(docs/planning/subscription-membership.md AC10, 2026-10-01): 무료배송 대신 멤버십 이벤트 혜택으로 준다.
-- 멤버십 혜택이 활성인 회원만 직접 받는다(수령형).
ALTER TABLE coupons
    ADD COLUMN membership_only BOOLEAN NOT NULL DEFAULT FALSE;

-- 같은 쿠폰을 한 회원이 두 번 받지 못하게 한다(수령 버튼 연타·동시 요청을 DB 가 막는다).
CREATE UNIQUE INDEX uq_issued_coupons_coupon_user ON issued_coupons (coupon_id, user_id);
