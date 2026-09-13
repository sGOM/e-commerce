-- ── 상품 리뷰 / 포토리뷰 ──────────────────────────────────────────────────
-- 구매 인증은 OrderItem(스냅샷) 을 그대로 활용한다(SubOrder.status = DELIVERED 인 항목만 작성 가능,
-- 서비스 레이어에서 검증). 상품 평점은 조회 트래픽이 훨씬 높으므로 Product 에 비정규화해 둔다
-- (리뷰 작성/수정/삭제/숨김 시점에만 재계산 — 읽기는 컬럼 그대로 사용).

-- 리뷰 적립 포인트의 어뷰징(삭제 후 재작성 반복) 방지 — Review 행과 독립적으로 OrderItem 에 영구 표시.
ALTER TABLE order_items
    ADD COLUMN review_rewarded BOOLEAN NOT NULL DEFAULT FALSE;

-- 상품 평점 요약(비정규화). 리뷰 변경 시 서비스가 재계산해 갱신한다.
ALTER TABLE products
    ADD COLUMN avg_rating NUMERIC(2, 1) NOT NULL DEFAULT 0,
    ADD COLUMN review_count INT NOT NULL DEFAULT 0;

-- 리뷰 적립/기간/신고 정책(관리자 설정, 단일 행). 포인트 적립액은 원(KRW) 정수.
CREATE TABLE review_reward_policies (
    id                 BIGSERIAL PRIMARY KEY,
    text_review_point  BIGINT      NOT NULL,
    photo_review_point BIGINT      NOT NULL,
    reviewable_days    INT         NOT NULL,
    report_threshold   INT         NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_review_reward_policy_non_negative
        CHECK (text_review_point >= 0 AND photo_review_point >= 0 AND reviewable_days >= 1 AND report_threshold >= 1)
);
INSERT INTO review_reward_policies
    (text_review_point, photo_review_point, reviewable_days, report_threshold, created_at, updated_at)
VALUES (100, 300, 90, 5, now(), now());

CREATE TABLE reviews (
    id             BIGSERIAL PRIMARY KEY,
    order_item_id  BIGINT       NOT NULL UNIQUE REFERENCES order_items (id),
    product_id     BIGINT       NOT NULL REFERENCES products (id),
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    author_name    VARCHAR(50)  NOT NULL,   -- 작성 시점 스냅샷(회원정보 변경과 무관, 목록 조회 시 조인 방지)
    rating         INT          NOT NULL,
    content        TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL,   -- VISIBLE / REPORTED / HIDDEN
    report_count   INT          NOT NULL DEFAULT 0,
    has_photo      BOOLEAN      NOT NULL DEFAULT FALSE, -- 포토리뷰 필터용 비정규화(이미지 존재 여부)
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_reviews_product ON reviews (product_id, status);
CREATE INDEX idx_reviews_user ON reviews (user_id);

CREATE TABLE review_images (
    id             BIGSERIAL PRIMARY KEY,
    review_id      BIGINT       NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    image_url      VARCHAR(500) NOT NULL,
    display_order  INT          NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_review_images_review ON review_images (review_id);

CREATE TABLE review_reports (
    id           BIGSERIAL PRIMARY KEY,
    review_id    BIGINT      NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    reporter_id  BIGINT      NOT NULL REFERENCES users (id),
    reason       VARCHAR(500) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_review_report UNIQUE (review_id, reporter_id) -- 동일 리뷰 중복 신고는 1회로 집계
);
