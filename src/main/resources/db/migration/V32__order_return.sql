-- 반품 요청(ROADMAP 1.5, 2026-09-30 정책 확정). 하위 주문(판매자) 단위로 요청→회수→검수 완료(환불)/거절.
-- 거절 후 재요청을 허용하므로 sub_order 당 여러 행이 생길 수 있다. 진행 중 요청은 하위 주문당 하나(아래 부분 유니크 인덱스).
CREATE TABLE order_returns (
    id                     BIGSERIAL PRIMARY KEY,
    sub_order_id           BIGINT       NOT NULL REFERENCES sub_orders (id),
    user_id                BIGINT       NOT NULL,
    reason                 VARCHAR(20)  NOT NULL, -- CHANGE_OF_MIND / DEFECTIVE / WRONG_DELIVERY
    detail                 VARCHAR(500),
    status                 VARCHAR(20)  NOT NULL, -- REQUESTED / COLLECTING / COMPLETED / REJECTED
    previous_sub_order_status VARCHAR(20) NOT NULL, -- 거절 시 되돌릴 상태(SHIPPED / DELIVERED)
    return_fee             BIGINT       NOT NULL,
    refund_amount          BIGINT       NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_return_amounts_non_negative CHECK (return_fee >= 0 AND refund_amount >= 0)
);
CREATE INDEX idx_order_returns_sub_order ON order_returns (sub_order_id);
-- 하위 주문당 진행 중 반품은 하나(동시 중복 요청을 DB 가 막는다).
CREATE UNIQUE INDEX uq_order_returns_active ON order_returns (sub_order_id) WHERE status IN ('REQUESTED', 'COLLECTING');
CREATE INDEX idx_order_returns_user ON order_returns (user_id);

-- 반품 정책값은 배송비 정책 행에 둔다: 단순변심 반품 배송비(왕복)와 구매확정 후 반품 가능 일수.
ALTER TABLE shipping_policies
    ADD COLUMN return_fee         BIGINT NOT NULL DEFAULT 6000,
    ADD COLUMN return_window_days INT    NOT NULL DEFAULT 7,
    ADD CONSTRAINT chk_shipping_return_fee_non_negative CHECK (return_fee >= 0),
    ADD CONSTRAINT chk_shipping_return_window_positive CHECK (return_window_days > 0);
