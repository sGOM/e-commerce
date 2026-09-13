-- ── 재입고 알림 / 인앱 알림함 ──────────────────────────────────────────────
-- 재고는 이미 InventoryRepository.reserve/release 로 원자적 UPDATE 정합성이 보장되어 있으므로,
-- 여기서는 "판매자가 재고를 절대값으로 조정할 때(0 → 양수 전이)"만 감지해 알림을 트리거한다
-- (SellerProductService.adjustStock, 이벤트 발행 → AFTER_COMMIT 비동기 처리).

-- 재입고 알림 신청. 옵션(SKU) 단위 — 상품이 아닌 옵션 단위로 신청받는다.
CREATE TABLE restock_alerts (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    option_id   BIGINT      NOT NULL REFERENCES product_options (id),
    status      VARCHAR(20) NOT NULL, -- PENDING / NOTIFIED / CANCELED
    notified_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_restock_alerts_option_status ON restock_alerts (option_id, status);
CREATE INDEX idx_restock_alerts_user ON restock_alerts (user_id);
-- 동일 사용자·옵션의 PENDING 신청은 1건만 허용(부분 유니크 인덱스). 서비스 계층의 existsBy... 체크와
-- 이중 방어 관계 — 동시 요청 경쟁에서도 DB 가 최종적으로 무결성을 보장한다.
CREATE UNIQUE INDEX uq_restock_alerts_pending ON restock_alerts (user_id, option_id) WHERE status = 'PENDING';

-- 범용 인앱 알림함. 재입고 전용이 아니라 향후 주문상태변경/쿠폰 등도 재사용할 수 있도록 type 컬럼으로
-- 구분한다(README 공통 오픈 이슈 — 이메일/푸시는 인프라 부재로 후속 과제, 인앱만 우선 구현).
CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    type       VARCHAR(30)  NOT NULL, -- RESTOCK / ...(향후 확장)
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    link_url   VARCHAR(500),
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);
-- 알림함 목록(최신순)·미확인 배지 카운트 조회에 함께 쓰인다
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, is_read);
