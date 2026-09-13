-- ── 선물하기 — docs/planning/gift-order.md ──────────────────────────────
-- 핵심 충돌 지점: 기존 orders 는 생성 시점에 배송지가 필수(V9)였다. 선물 주문은 "결제는 즉시,
-- 배송지는 수령자가 나중에 입력"을 표현해야 하므로 배송지 컬럼을 nullable 로 완화하고, 대신
-- "선물이 아니면 배송지가 반드시 있어야 한다"는 CHECK 제약으로 무결성을 지킨다(§9 오픈이슈 #1).
--
-- 재고/결제/배송지 확정 시점 분리: 재고 예약·결제는 기존 흐름 그대로 주문 생성/결제 시점에 끝나고
-- (AC1/AC2), 배송지만 GiftClaim 수락 시점으로 유예된다. SubOrder 가 SHIPPED 로 전이하려면(발송)
-- orders.receiver_name 등이 채워져 있어야 하며, 이 가드는 애플리케이션 레이어
-- (SubOrder.isShippable, com.example.starter.domain.order.entity.SubOrder)에서 강제한다.
--
-- 개인정보 보호(§4): 수령자가 입력한 배송지는 구매자에게 노출하지 않는다 — 이 요구는 스키마가 아니라
-- 응답 DTO 계층(OrderResponse.from, isGift 인 경우 shippingAddress 마스킹)에서 처리한다.
ALTER TABLE orders
    ALTER COLUMN receiver_name DROP NOT NULL,
    ALTER COLUMN receiver_phone DROP NOT NULL,
    ALTER COLUMN zipcode DROP NOT NULL,
    ALTER COLUMN address1 DROP NOT NULL,
    ADD COLUMN is_gift     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN gift_message TEXT;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_shipping_required_unless_gift CHECK (
        is_gift OR (
            receiver_name IS NOT NULL AND receiver_phone IS NOT NULL
            AND zipcode IS NOT NULL AND address1 IS NOT NULL
        )
    );

-- 선물 링크(토큰) — 1개 주문당 1개(멀티셀러라도 수령자는 1명, §4). 수령자는 비회원도 접근 가능해야
-- 하므로(§4 게스트 선물) FK/회원 식별자를 두지 않고 순수 토큰 기반으로 조회한다.
CREATE TABLE gift_claims (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES orders (id),
    token       VARCHAR(64)  NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING / CLAIMED / EXPIRED / CANCELED
    expires_at  TIMESTAMPTZ  NOT NULL,
    claimed_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_gift_claims_order UNIQUE (order_id),
    CONSTRAINT uk_gift_claims_token UNIQUE (token)
);
-- 미수락 만료 배치 대상 조회(status='PENDING' AND expires_at <= now) 전용 인덱스.
CREATE INDEX idx_gift_claims_status_expires_at ON gift_claims (status, expires_at);

-- 선물 링크 정책(단일 행, point_policies/delivery_subscription_policies 와 동일 패턴).
CREATE TABLE gift_policies (
    id           BIGSERIAL PRIMARY KEY,
    expiry_days  INT         NOT NULL DEFAULT 7, -- 링크 만료기한(§9 오픈이슈 #2, 운영 정책 확정 전 기본값)
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_gift_policies_expiry_days CHECK (expiry_days >= 1)
);
INSERT INTO gift_policies (expiry_days, created_at, updated_at) VALUES (7, now(), now());
