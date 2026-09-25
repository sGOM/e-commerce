-- 상품 Q&A(ROADMAP 2.4, docs/planning/product-qna.md). 고객 문의는 비밀(작성자·판매자만 열람), 공개는 FAQ 테이블.
CREATE TABLE product_inquiries (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT        NOT NULL REFERENCES products (id),
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    question    VARCHAR(1000) NOT NULL,
    answer      VARCHAR(2000),
    answered_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_product_inquiries_product ON product_inquiries (product_id, id DESC);
CREATE INDEX idx_product_inquiries_user ON product_inquiries (user_id, id DESC);

CREATE TABLE product_faqs (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT        NOT NULL REFERENCES products (id),
    question   VARCHAR(500)  NOT NULL,
    answer     VARCHAR(2000) NOT NULL,
    sort_order INT           NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX idx_product_faqs_product ON product_faqs (product_id, sort_order, id);
