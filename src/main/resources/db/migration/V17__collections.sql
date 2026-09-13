-- ── 기획전 / 컬렉션 큐레이션 ─────────────────────────────────────────────
-- MD(관리자)가 상품을 수동으로 묶어 배너/제목과 함께 노출하는 "편집 진열" 컬렉션.
-- 카테고리(분류 체계)와 달리 마케팅 메시지 중심 묶음이며, Product 는 참조만 한다(수정 없음).
-- 이번 범위는 관리자 전용 편성이다(docs/planning/curated-collections.md §8 Out of scope — 셀러
-- 신청 플로우는 후속 검토).

CREATE TABLE collections (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(200) NOT NULL,
    subtitle          VARCHAR(300),
    banner_image_url  VARCHAR(500),
    start_at          TIMESTAMPTZ  NOT NULL,
    end_at            TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(20)  NOT NULL, -- DRAFT / PUBLISHED / ENDED
    display_order     INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_collection_period CHECK (end_at > start_at)
);
-- 고객 노출 목록 조회: status + 기간 필터 + displayOrder 정렬에 함께 쓰인다.
CREATE INDEX idx_collections_status_period ON collections (status, start_at, end_at);
CREATE INDEX idx_collections_display_order ON collections (display_order);

-- 컬렉션 편성 상품. product_id 는 catalog.products 를 참조만 하는 비정규화 id
-- (다른 애그리거트 참조는 이 코드베이스 관례대로 연관관계 대신 순수 id 로 둔다 — Review.productId 참고).
-- 상품 상태(HIDDEN 등) 변경 시 별도 동기화 로직 없이 조회 시점에 필터링한다(기획서 §7).
CREATE TABLE collection_products (
    id             BIGSERIAL PRIMARY KEY,
    collection_id  BIGINT NOT NULL REFERENCES collections (id) ON DELETE CASCADE,
    product_id     BIGINT NOT NULL REFERENCES products (id),
    display_order  INT    NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_collection_product UNIQUE (collection_id, product_id)
);
CREATE INDEX idx_collection_products_collection ON collection_products (collection_id, display_order);
