-- ── 타임딜 / 한정특가 (flash-sale) ────────────────────────────────────────
-- 옵션 단위 한정 시간·한정 수량 특가. 재고와 별개로 sold_quantity 를 원자적 UPDATE 로 관리해
-- 초과 판매를 막는다(docs/planning/flash-sale.md §5). product_options/sellers 는 참조만 하고
-- 수정하지 않는다(다른 애그리거트 참조는 이 코드베이스 관례대로 연관관계 대신 순수 id —
-- Collection.productId 참고). seller_id 는 정산·권한 조회 편의를 위한 비정규화 컬럼이다.
--
-- status 는 관리자 강제 종료(CANCELED) 여부만 관리하는 "행정 상태"이며, 지금 이 시각에
-- 진행중/예정/종료인지(SCHEDULED/ONGOING/ENDED)는 start_at/end_at/sold_quantity 로부터
-- 애플리케이션이 매 조회 시점에 파생 계산한다(배치 잡 없이 정확한 실시간 상태를 보장하기 위함).

CREATE TABLE flash_sales (
    id                 BIGSERIAL PRIMARY KEY,
    product_option_id  BIGINT      NOT NULL REFERENCES product_options (id),
    seller_id          BIGINT      NOT NULL REFERENCES sellers (id),
    original_price     BIGINT      NOT NULL,
    sale_price         BIGINT      NOT NULL,
    limit_quantity     INT         NOT NULL,
    sold_quantity      INT         NOT NULL DEFAULT 0,
    start_at           TIMESTAMPTZ NOT NULL,
    end_at             TIMESTAMPTZ NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / CANCELED(관리자 강제 종료)
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_flash_sale_period CHECK (end_at > start_at),
    CONSTRAINT chk_flash_sale_price CHECK (sale_price > 0 AND sale_price < original_price),
    CONSTRAINT chk_flash_sale_quantity CHECK (limit_quantity > 0 AND sold_quantity >= 0 AND sold_quantity <= limit_quantity)
);
-- AC2(겹치는 기간 등록 방지) 검증과 옵션별 진행 딜 조회에 함께 쓰인다.
CREATE INDEX idx_flash_sales_option_period ON flash_sales (product_option_id, start_at, end_at);
-- 공개 목록(진행 중 딜, 종료 임박순) 조회용.
CREATE INDEX idx_flash_sales_status_period ON flash_sales (status, start_at, end_at);
-- 판매자 본인 타임딜 목록 조회용.
CREATE INDEX idx_flash_sales_seller ON flash_sales (seller_id);

-- ── order_items 확장 — 타임딜 주문 시점 스냅샷 ───────────────────────────
-- unit_price 는 기존과 동일하게 "정가"(기본가+옵션추가금) 스냅샷을 유지하고,
-- applied_sale_price 는 타임딜이 적용된 경우의 실제 청구 단가다(없으면 NULL = 정가 그대로 청구).
-- 실 결제 라인 합계(line_total)는 applied_sale_price 가 있으면 그 값을, 없으면 unit_price 를 사용해
-- 애플리케이션에서 계산한다(기존 "unit_price * quantity" 규칙의 확장).
ALTER TABLE order_items
    ADD COLUMN flash_sale_id      BIGINT REFERENCES flash_sales (id),
    ADD COLUMN applied_sale_price BIGINT;
CREATE INDEX idx_order_items_flash_sale ON order_items (flash_sale_id);
