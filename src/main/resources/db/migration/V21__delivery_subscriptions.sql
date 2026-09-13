-- ── 정기배송 구독 — docs/planning/subscription-delivery.md ──────────────
-- 정기결제(빌링) 인프라는 유료 멤버십(V20__memberships.sql)에서 신설한 공용 포트/어댑터
-- (com.example.starter.domain.billing.gateway.BillingKeyGateway)를 그대로 재사용한다. 다만 "카드
-- 등록" 테이블 자체는 멤버십과 통합하지 않고 도메인별로 분리했다(§9 오픈이슈 #1 — 회원이 두 기능에
-- 서로 다른 카드를 쓸 수 있게 하고, 한 도메인 스키마 변경이 다른 도메인에 영향을 주지 않게 하려는
-- 목적. 후속 과제: "결제수단" 공용 도메인 통합 검토).
--
-- delivery_subscriptions 는 상품 옵션 1개당 구독 1행이다(여러 상품을 묶은 정기 박스는 범위 밖, §8).
-- 배송지는 저장된 주소록 도메인이 없어(기획서 §5의 addressId FK 대신) orders 테이블과 동일하게
-- 임베디드 값 객체 컬럼으로 스냅샷한다.
--
-- status 는 ACTIVE/PAUSED/CANCELED 3가지 — 멤버십과 달리 PAST_DUE(결제유예) 상태를 두지 않는다.
-- 정기배송은 실패 시 유예 없이 즉시 해당 회차를 스킵하고 다음 주기로 넘어가는 것이 원칙이기 때문이다
-- (AC7/AC8, 소모품 정기배송에서 "언제 될지 모르는 유예"보다 "다음 정기 주기에 재시도"가 UX상 합리적).

CREATE TABLE delivery_subscriptions (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     BIGINT      NOT NULL REFERENCES users (id),
    option_id                   BIGINT      NOT NULL REFERENCES product_options (id),
    quantity                    INT         NOT NULL,
    cycle_days                  INT         NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / PAUSED / CANCELED
    next_order_at               TIMESTAMPTZ NOT NULL,
    skip_requested              BOOLEAN     NOT NULL DEFAULT FALSE, -- 다음 회차 1회 건너뛰기 요청(AC5)
    consecutive_failure_count   INT         NOT NULL DEFAULT 0,
    canceled_at                 TIMESTAMPTZ,
    orderer_name                VARCHAR(100) NOT NULL,
    orderer_phone               VARCHAR(30)  NOT NULL,
    orderer_email               VARCHAR(255) NOT NULL,
    receiver_name                VARCHAR(100) NOT NULL,
    receiver_phone               VARCHAR(30)  NOT NULL,
    zipcode                     VARCHAR(10)  NOT NULL,
    address1                    VARCHAR(255) NOT NULL,
    address2                    VARCHAR(255),
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_delivery_subscriptions_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_delivery_subscriptions_cycle_days CHECK (cycle_days >= 1),
    CONSTRAINT chk_delivery_subscriptions_failure_count CHECK (consecutive_failure_count >= 0)
);
-- 배치 대상 조회(status='ACTIVE' AND next_order_at <= now) + 회원 마이페이지 목록 양쪽에 쓰인다.
CREATE INDEX idx_delivery_subscriptions_status_next_order ON delivery_subscriptions (status, next_order_at);
CREATE INDEX idx_delivery_subscriptions_user ON delivery_subscriptions (user_id);

-- 회원당 등록된 빌링키(카드) 1개만 유지 — 재등록 시 기존 행을 갱신(교체)한다.
CREATE TABLE delivery_subscription_billing_keys (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id),
    gateway_billing_key VARCHAR(200) NOT NULL,
    card_last4          VARCHAR(4)   NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_delivery_subscription_billing_keys_user UNIQUE (user_id)
);

-- 회차별 처리 이력(append-only, AC10 마이페이지 타임라인). 결제(payments) 테이블처럼 orderId UNIQUE
-- 제약을 두지 않는다 — 같은 구독이라도 회차마다 새 주문이 생성되므로 자연히 회차당 1건이며, 이력
-- 자체가 "이번 회차를 이미 처리했는지"의 근거는 아니다(그 역할은 delivery_subscriptions.next_order_at
-- 이 담당 — 전진해야만 다음 배치 대상이 되므로 중복 처리가 애초에 불가능하다).
CREATE TABLE delivery_subscription_histories (
    id              BIGSERIAL PRIMARY KEY,
    subscription_id BIGINT      NOT NULL REFERENCES delivery_subscriptions (id),
    attempted_at    TIMESTAMPTZ NOT NULL,
    result          VARCHAR(30) NOT NULL, -- ORDER_CREATED / SKIPPED_OUT_OF_STOCK / SKIPPED_BY_USER / PAYMENT_FAILED / PAUSED_PRODUCT_UNAVAILABLE
    order_id        BIGINT REFERENCES orders (id),
    detail          VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_delivery_subscription_histories_subscription ON delivery_subscription_histories (subscription_id, id DESC);

-- 정기배송 정책(단일 행, membership_policies 와 동일 패턴 — 관리자가 런타임 변경).
CREATE TABLE delivery_subscription_policies (
    id                          BIGSERIAL PRIMARY KEY,
    max_consecutive_failures    INT         NOT NULL DEFAULT 3, -- 연속 결제실패 임계치(AC8) — 도달 시 자동 PAUSED
    skip_deadline_days          INT         NOT NULL DEFAULT 1, -- 다음 배송일 T일 전까지만 스킵 요청 허용(AC5)
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_delivery_subscription_policies_failures CHECK (max_consecutive_failures >= 1),
    CONSTRAINT chk_delivery_subscription_policies_skip_days CHECK (skip_deadline_days >= 0)
);
INSERT INTO delivery_subscription_policies (max_consecutive_failures, skip_deadline_days, created_at, updated_at)
VALUES (3, 1, now(), now());
