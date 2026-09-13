-- ── 배송 슬롯 예약 (새벽배송/시간대 지정, docs/planning/delivery-slot.md) ──────────────
-- 슬롯 정원(reserved_count/capacity)은 재고(inventories)·타임딜(flash_sales.sold_quantity)과
-- 동일한 패턴의 단일 원자적 UPDATE 로 관리한다(DeliverySlotRepository.reserve/release).
-- region_scope 는 delivery_regions.postal_code_prefix 와 같은 접두사 문자열이며, NULL 이면
-- 전국 공통 슬롯이다. delivery_regions 는 "새벽배송 자체가 가능한 지역인지"를 판정하는
-- 화이트리스트(우편번호 접두사)로, 슬롯의 region_scope 와는 별개 개념이다(문서 §9 오픈이슈 참고).

CREATE TABLE delivery_regions (
    id                       BIGSERIAL PRIMARY KEY,
    postal_code_prefix       VARCHAR(10)  NOT NULL,
    dawn_delivery_available  BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ  NOT NULL,
    updated_at               TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_delivery_regions_prefix UNIQUE (postal_code_prefix)
);

CREATE TABLE delivery_slots (
    id              BIGSERIAL PRIMARY KEY,
    slot_date       DATE         NOT NULL,
    start_time      TIME         NOT NULL,
    end_time        TIME         NOT NULL,
    type            VARCHAR(20)  NOT NULL, -- DAWN / DAYTIME
    cutoff_at       TIMESTAMPTZ  NOT NULL,
    capacity        INT          NOT NULL,
    reserved_count  INT          NOT NULL DEFAULT 0,
    -- 특정 권역 전용 슬롯이면 postal_code_prefix 접두사 문자열, 전국 공통이면 NULL(AC2).
    region_scope    VARCHAR(10),
    -- 슬롯(주로 새벽배송) 이용 시 추가 배송비. 공통 배송비 모델이 아직 없어(오픈 이슈) 슬롯 단위로만
    -- 우선 도입한다 — SubOrder.delivery_fee 로 스냅샷되어 실제 결제금액에 반영된다(OrderService 참고).
    extra_fee       BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_delivery_slot_period CHECK (end_time > start_time),
    CONSTRAINT chk_delivery_slot_capacity CHECK (capacity > 0 AND reserved_count >= 0 AND reserved_count <= capacity),
    CONSTRAINT chk_delivery_slot_fee CHECK (extra_fee >= 0)
);
-- 공개 조회(GET /api/delivery-slots?postalCode=&date=)의 주 조회 패턴: 날짜 우선 필터.
CREATE INDEX idx_delivery_slots_date_type ON delivery_slots (slot_date, type);
CREATE INDEX idx_delivery_slots_region ON delivery_slots (region_scope);

-- ── sub_orders 확장 — SubOrder 단위 슬롯 예약(AC6) ──────────────────────────────
ALTER TABLE sub_orders
    ADD COLUMN delivery_slot_id BIGINT REFERENCES delivery_slots (id),
    ADD COLUMN delivery_fee     BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_sub_orders_delivery_slot ON sub_orders (delivery_slot_id);

-- ── orders 확장 — 슬롯 추가요금 합계(결제금액 계산에 반영, Order.recalculateAmounts) ──
ALTER TABLE orders
    ADD COLUMN delivery_fee_total BIGINT NOT NULL DEFAULT 0;

-- ── products 확장 — 새벽배송 가능 상품 여부(AC3) ────────────────────────────────
-- 오픈 이슈 #2(상품 단위 vs 셀러 단위)는 문서 초안대로 상품 단위 필드로 확정한다(아래 참고).
ALTER TABLE products
    ADD COLUMN dawn_delivery_eligible BOOLEAN NOT NULL DEFAULT FALSE;
