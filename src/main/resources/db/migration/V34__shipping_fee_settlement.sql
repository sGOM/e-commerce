-- 배송비 정산 귀속(ROADMAP 7.3, 2026-10-01 사용자 결정): 배송비는 판매자가 자기 택배사로 내는 실비라
-- 수수료 없이 판매자에게 지급한다(스마트스토어 방식). 정산서에 배송비 몫을 따로 남긴다.
ALTER TABLE settlements
    ADD COLUMN delivery_fee_amount BIGINT NOT NULL DEFAULT 0;

-- 단순변심 반품 시 고객 환불에서 뺀 반품 배송비는 회수 택배비를 낸 판매자 몫이다. 반품 완료 때 기록하고 정산에 더한다.
-- 불량·오배송 회수비는 판매자가 자기 택배사에 직접 내므로(판매자 부담) 0 으로 남는다.
ALTER TABLE sub_orders
    ADD COLUMN seller_return_fee BIGINT NOT NULL DEFAULT 0;

-- 배송비가 판매자 몫이 되면서 멤버십 무료배송 혜택을 없앤다(적립·이벤트 혜택으로 대체).
ALTER TABLE membership_policies
    DROP COLUMN free_shipping_enabled;
