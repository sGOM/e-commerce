-- 상품 대표 이미지(ROADMAP 2.1). 업로드 API(`/api/uploads`) 결과 URL 또는 외부 URL 을 저장한다.
ALTER TABLE products ADD COLUMN image_url VARCHAR(500);
