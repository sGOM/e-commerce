# 추가 구현 로드맵 (Backlog)

> 최종 갱신: 2026-09-17 — main 코드와 대조해 **완료 항목은 제거하고 남은 작업만** 남겼다.
> 기능 기획("무엇을·왜")은 [`docs/planning/`](./planning/README.md), 이 문서는 **아직 코드에 없는 것**의 백로그다.

- 우선순위: 🔴 높음 · 🟡 중간 · 🟢 낮음
- 작업량: S(1일 이내) · M(2~3일) · L(1주 내외)

> 현재 스택: Kotlin · Spring Boot 3.5 · PostgreSQL/Flyway · 세션 인증/RBAC · React+Vite+TS 스토어프론트

## 이미 완료되어 백로그에서 제거된 항목

주문 취소/환불(`doCancel`, 관리자 환불), SubOrder 부분 취소, 상품 리뷰/포토리뷰, 재입고 알림·위시리스트,
송장 등록(`POST /api/seller/orders/{subOrderId}/ship`), 정산 지급(`PATCH /api/admin/settlements/{id}/pay`),
인앱 알림함(`/api/me/notifications`), 카테고리 계층 엔티티(`categories.parent_id`),
CI(`.github/workflows/ci.yml` — 백엔드 테스트 + 프론트 lint/build), 판매자 상품 목록(`GET /api/seller/products`),
전체 정산 목록(`GET /api/admin/settlements?status=`), 상품 검색 정렬·가격 범위·하위 카테고리 포함
(`GET /api/products?sort=&minPrice=&maxPrice=&categoryId=`), 판매자·관리자 라우트 lazy 분할(index 640→514KB),
프론트 테스트 기반(Vitest + happy-dom, CI `npm test`), 판매자 승인 즉시 권한 반영(`GET /api/auth/me` 가 세션 권한 갱신), 배송지 주소록(`/api/me/addresses`, 체크아웃 불러오기),
프론트 의존성 취약점 0건(`npm audit fix`), 비밀번호 변경(`PATCH /api/auth/password`, 소셜 전용 계정은 신규 설정),
Testcontainers 로컬 Docker 29 호환(1.21.4, 외부 PG 우회 불필요),
카테고리 수정/삭제(`PUT·DELETE /api/admin/categories/{id}`, 순환 상위 차단, 하위·상품 있으면 삭제 거부),
장바구니·체크아웃 화면 테스트(Testing Library — 재고 부족 차단, 수량 갱신, 기본 배송지·쿠폰 미리보기, 비회원 주문),
API 문서(springdoc — `/swagger-ui/index.html`, `/v3/api-docs`, prod 프로필에서는 비활성),
판매자 매출 대시보드(`GET /api/seller/dashboard?from=&to=` — 기간 주문 수·매출, 미정산 판매액, 지급 대기액),
관리자 회원·포인트·감사 로그 화면(`/admin/users` 상태·역할 관리, `/admin/points` 적립 정책·만료 실행, `/admin/audit-logs`),
이미지 업로드(`POST /api/uploads` 시그니처 검증·로컬 디스크 저장, 상품 대표 이미지 `products.image_url`, 리뷰 사진 업로드),
관리자 대시보드(`GET /api/admin/dashboard?from=&to=` — GMV·주문 수·신규 가입, 일별 추이),
Prometheus 메트릭(`/actuator/prometheus` — JVM·HTTP·Hikari·`@Scheduled` 실행 지표, 운영은 내부 포트 `MANAGEMENT_PORT` 분리),
저재고 알림(주문 예약으로 가용재고가 `inventory.low-stock-threshold`(기본 5) 이하로 내려가면 판매자 인앱 알림 `LOW_STOCK`),
게스트 결제(`POST /api/payments/guest` — 주문번호+연락처 확인, 체크아웃 즉시 결제·조회 화면 재시도),
재고 CSV 일괄 수정(`PATCH /api/seller/products/stock` — `sku,quantity` 전량 검증 후 일괄 반영),
이메일 알림 채널(`EmailSender` — `spring.mail.host` 유무로 SMTP/로깅 구현 선택, 재입고·가격인하·멤버십·정기배송·선물 알림 발송)),
비밀번호 분실 재설정(`POST /api/auth/password-reset/request|confirm` — 메일 토큰 30분·1회용, 해시 저장, 계정 열거 방지, `/reset-password` 화면),
PG 결제 취소 연동(`PaymentGateway.cancel` — 전체·부분 취소 금액을 토스 `/v1/payments/{paymentKey}/cancel` 로, 결정적 `Idempotency-Key`, PG 거절 시 전체 롤백),
결제 웹훅(`POST /api/payments/webhook/toss` — 서명 없는 PAYMENT_STATUS_CHANGED 를 PG 재조회로 검증, PG 전액 취소를 주문에 반영),
토스 결제창 프론트 연동(`VITE_TOSS_CLIENT_KEY` 설정 시 SDK v2 결제창 → `/payments/toss/success` 에서 `paymentKey` 로 서버 승인, 회원·비회원·주문 상세 재결제, 키 없으면 Mock PG),
업로드 저장소 추상화(`ImageStorage` — `upload.storage=local|s3`, S3/MinIO 호환, 공개 URL `/api/uploads/{name}` 유지),
상품 Q&A(`docs/planning/product-qna.md` — 고객 문의는 비밀(작성자·판매자만), 공개는 판매자 FAQ, 문의·답변 인앱 알림 `PRODUCT_QNA`, 판매자 `/seller/inquiries`),
회원 탈퇴(`POST /api/auth/withdraw` — soft delete: `WITHDRAWN`·`withdrawn_at`, 비밀번호 확인, 배송 중 주문·판매자 거부, 미결제 주문·멤버십·정기배송 해지와 빌링키 삭제, 소셜 로그인도 차단),
인센티브 어뷰징 방지 원칙 확정(`docs/planning/README.md` 공통 오픈 이슈 5 — 1인 1회·자기추천 차단·구매확정 후 지급/회수·월 상한),
기본 배송비(판매자 단위 3,000원 — `shipping_policies` 정책 행, `GET /api/shipping-policy`·`PATCH /api/admin/shipping-policy`, 멤버십 무료배송 면제, 포인트 적립·로열티 산정은 배송비 제외),
미결제 주문 자동 만료(CREATED 30분 경과 시 취소·재고 복원, 결제와 주문 행 잠금으로 경합 차단, `order.unpaid-expiry.scheduler.enabled` 또는 `POST /api/admin/orders/expire-unpaid/run`).

**5. 관리자 백오피스** 는 남은 항목이 없어 표를 제거했다(번호는 기존 항목 ID 유지를 위해 재사용하지 않는다).

---

## 1. 결제 / 주문 흐름 완성

실거래 전환의 가장 큰 공백. 결제는 Mock PG 기준으로만 닫혀 있다.

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 1.5 | **반품·교환 상태 머신** | 🟢 | L | - | 현재는 관리자 환불로 대체. 반품 요청→회수→검수→환불 흐름 |

## 6. 알림 / 메시징

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 6.2 | **배송 추적 연동** | 🟢 | M | - | 택배사 API, 배송 상태 노출 |
| 6.3 | **웹 푸시** | 🟢 | L | 6.1 | |

## 7. 정책 선행 과제 (기획 오픈 이슈)

`docs/planning/README.md` 공통 오픈 이슈 중 미해결 항목. 구현 전 정책 확정 필요.

| # | 항목 | 우선순위 | 메모 |
|---|------|:------:|------|
| 7.3 | **배송비 정산 귀속** | 🟡 | 기본 배송비(7.1)는 현재 정산(`subtotal` 기준)에서 제외돼 플랫폼 몫이 된다. 판매자에게 지급할지 결정 필요 |

## 8. 품질 / 운영 (Cross-cutting)

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 8.3 | **프론트 번들 추가 분할** | 🟢 | S | - | 2026-09-18 측정: index 520KB(gzip 150KB)이고 대부분 React·router·radix 공용 라이브러리라 고객 페이지 lazy 분할 이득이 작다(로딩 깜빡임 트레이드오프). 무거운 단일 의존성이 새로 생기면 재검토 |
| 8.6 | **캐싱** | 🟢 | M | - | 인기 상품/카테고리 매 요청 집계 |
| 8.8 | **검색 인프라** | 🟢 | L | - | ILIKE → pg_trgm/전문검색 |

---

## 남은 항목이 막힌 이유

| 구분 | 항목 | 필요한 것 |
|------|------|-----------|
| 외부 연동 키(실검증만) | 1.5 | 결제 1.2~1.4 는 코드·테스트 완료. 실결제 확인에만 Toss 테스트 키(클라이언트/시크릿)와 웹훅 URL 등록 필요 |
| 외부 연동 키 | (실발송만) | 6.1·3.1 코드는 완료 — 실제 메일 발송에만 SMTP 계정 필요(`MAIL_HOST` 등) |
| 외부 연동 키 | 6.2, 6.3 | 택배사 API 계약 / 웹푸시 VAPID |
| 정책 결정 | 7.3 | 배송비를 판매자 정산에 포함할지 |
| 측정 결과 보류 | 8.3, 8.6, 8.8 | 현재 규모에서 이득이 확인되지 않음 |

## 추천 진행 순서

1. **결제 실검증** — Toss 테스트 키로 결제·취소·웹훅 확인(키 발급은 사용자 작업)
2. **배송 추적(6.2)** — 택배사 조회 인터페이스 + Mock, 실 API 키는 설정만
3. **정책 확정 후** — 7.3 배송비 정산 귀속
