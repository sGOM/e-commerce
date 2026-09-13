-- ── 유료 멤버십(구독) — docs/planning/subscription-membership.md ──────────────
-- 정기결제(빌링키) 인프라가 전무했으므로 이 기능과 함께 공용 추상화
-- (com.example.starter.domain.billing.gateway.BillingKeyGateway)를 신설한다. 이 도메인 자체는
-- 빌링키/청구이력 테이블만 갖고, 실제 PG 통신은 게이트웨이 구현체(Mock)가 흉내낸다.
--
-- memberships 는 회원당 1행을 재사용한다("unique-ish 활성 구독은 1개" — 기획서 §5). 해지/만료 후
-- 재가입해도 새 행을 만들지 않고 같은 행의 상태를 초기화한다(이력은 membership_billing_histories 에
-- 남으므로 재가입 이력 추적은 그쪽에서 가능). status 는 ACTIVE/PAST_DUE/CANCELED/EXPIRED 4가지이며,
-- CANCELED 는 "해지 예약 — 이미 결제한 기간까지는 혜택 유지"(AC3)를 표현한다. canceled_at 이 채워진
-- 상태에서 next_billing_at 이 지나면 스케줄러가 EXPIRED 로 전이한다(혜택 종료).

CREATE TABLE memberships (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT      NOT NULL REFERENCES users (id),
    plan                    VARCHAR(20) NOT NULL DEFAULT 'BASIC',
    status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / PAST_DUE / CANCELED / EXPIRED
    price                   BIGINT      NOT NULL, -- 구독 시작(갱신) 시점 정책가 스냅샷
    start_at                TIMESTAMPTZ NOT NULL,
    next_billing_at         TIMESTAMPTZ NOT NULL,
    canceled_at             TIMESTAMPTZ, -- 해지 예약 시각(NULL 이면 정상 자동갱신 대상)
    billing_failure_count   INT         NOT NULL DEFAULT 0,
    grace_period_ends_at    TIMESTAMPTZ, -- PAST_DUE 유예 만료 시각(이 시각에 재시도/최종 만료 판단)
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_memberships_user UNIQUE (user_id),
    CONSTRAINT chk_memberships_price CHECK (price >= 0),
    CONSTRAINT chk_memberships_failure_count CHECK (billing_failure_count >= 0)
);
-- 관리자 검색(GET /api/admin/memberships?status=)과 배치 대상(다음 결제일 도래) 조회에 함께 쓰인다.
CREATE INDEX idx_memberships_status_next_billing ON memberships (status, next_billing_at);

-- 회원당 등록된 빌링키(카드) 1개만 유지 — 재등록 시 기존 행을 갱신(교체)한다.
CREATE TABLE membership_billing_keys (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL REFERENCES users (id),
    gateway_billing_key VARCHAR(200) NOT NULL, -- PG(Mock/Toss) 발급 토큰
    card_last4          VARCHAR(4)   NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_membership_billing_keys_user UNIQUE (user_id)
);

-- 정기결제 시도 이력(성공/실패 모두 append). cycle_at 은 "이번 청구가 해당하는 주기"의 식별자로,
-- 청구 시점의 memberships.next_billing_at 값을 스냅샷한다 — 재시도는 같은 cycle_at 으로 여러 행이
-- 쌓일 수 있으나(FAILED 반복 가능), SUCCESS 는 같은 주기에 단 1건만 허용해 중복 청구를 막는다
-- ("이번 주기 청구 ID 단위 멱등 처리" — 기획서 §4). 애플리케이션 체크만으로는 스케줄러가 겹쳐 실행되는
-- 경쟁 상황(다중 인스턴스)을 완전히 막지 못하므로, DB 레벨에서 status='SUCCESS' 에 한해 부분 유니크
-- 인덱스로 강제한다.
-- 참고: PostgreSQL partial index — https://www.postgresql.org/docs/current/indexes-partial.html
CREATE TABLE membership_billing_histories (
    id                    BIGSERIAL PRIMARY KEY,
    membership_id         BIGINT       NOT NULL REFERENCES memberships (id),
    cycle_at              TIMESTAMPTZ  NOT NULL,
    attempted_at           TIMESTAMPTZ  NOT NULL,
    status                VARCHAR(20)  NOT NULL, -- SUCCESS / FAILED
    failure_reason        VARCHAR(500),
    -- 결제(payments) 테이블은 주문(Order) 1회성 결제 전용이라 FK 로 묶지 않는다(정기결제는 주문에
    -- 연결되지 않음). 게이트웨이가 반환한 거래번호만 참조 정보로 스냅샷한다.
    gateway_transaction_id VARCHAR(100),
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_membership_billing_histories_membership ON membership_billing_histories (membership_id, cycle_at);
CREATE UNIQUE INDEX uk_membership_billing_histories_success_cycle
    ON membership_billing_histories (membership_id, cycle_at)
    WHERE status = 'SUCCESS';

-- 멤버십 정책(단일 행, point_policies/settlement_policies 와 동일 패턴 — 관리자가 런타임 변경).
CREATE TABLE membership_policies (
    id                        BIGSERIAL PRIMARY KEY,
    monthly_price             BIGINT      NOT NULL,
    -- basis point, 10000 = 1.0배. earnRateBp 에 곱해 적용(AC9).
    point_earn_multiplier_bp  INT         NOT NULL DEFAULT 10000,
    free_shipping_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    max_retry_count           INT         NOT NULL DEFAULT 3,
    grace_days                INT         NOT NULL DEFAULT 3,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_membership_policies_price CHECK (monthly_price >= 0),
    CONSTRAINT chk_membership_policies_multiplier CHECK (point_earn_multiplier_bp >= 0),
    CONSTRAINT chk_membership_policies_retry CHECK (max_retry_count >= 1),
    CONSTRAINT chk_membership_policies_grace CHECK (grace_days >= 0)
);
INSERT INTO membership_policies (monthly_price, point_earn_multiplier_bp, free_shipping_enabled, max_retry_count, grace_days, created_at, updated_at)
VALUES (4900, 15000, TRUE, 3, 3, now(), now());
