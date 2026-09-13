-- ── 주문 배송지 (Phase 5) ────────────────────────────────────────────────
-- 배송은 SubOrder(판매자) 단위로 송장이 발급되지만, 배송지는 주문(Order) 1건에 귀속한다.
-- 기존 데이터가 없으므로 NOT NULL DEFAULT '' 로 추가한다(신규 주문은 항상 값을 채운다).

ALTER TABLE orders
    ADD COLUMN receiver_name  VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN receiver_phone VARCHAR(30)  NOT NULL DEFAULT '',
    ADD COLUMN zipcode        VARCHAR(10)  NOT NULL DEFAULT '',
    ADD COLUMN address1       VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN address2       VARCHAR(255);
