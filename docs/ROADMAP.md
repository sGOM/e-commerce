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
비밀번호 분실 재설정(`POST /api/auth/password-reset/request|confirm` — 메일 토큰 30분·1회용, 해시 저장, 계정 열거 방지, `/reset-password` 화면).

**5. 관리자 백오피스** 는 남은 항목이 없어 표를 제거했다(번호는 기존 항목 ID 유지를 위해 재사용하지 않는다).

---

## 1. 결제 / 주문 흐름 완성

실거래 전환의 가장 큰 공백. 결제는 Mock PG 기준으로만 닫혀 있다.

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 1.2 | **Toss 결제위젯 프론트 연동** | 🔴 | M | - | 백엔드 `TossPaymentGateway.approve`(금액 위변조 검증)는 완료. 프론트에 위젯 SDK + `paymentKey` 전달 흐름 없음 |
| 1.3 | **PG 결제 취소 API 연동** | 🔴 | M | 1.2 | `PaymentGateway`에 `approve`만 있다. 취소/부분환불 시 내부 상태만 바뀌고 PG 취소(`/v1/payments/{paymentKey}/cancel`)는 호출되지 않음 |
| 1.4 | **결제 Webhook 수신** | 🟡 | M | 1.2 | Toss 비동기 상태 변경(취소/가상계좌 입금) 수신 + 검증 |
| 1.5 | **반품·교환 상태 머신** | 🟢 | L | 1.3 | 현재는 관리자 환불로 대체. 반품 요청→회수→검수→환불 흐름 |

## 2. 상품 / 카탈로그

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 2.4 | **상품 Q&A** | 🟢 | M | - | 상품 문의/답변, 판매자 알림 |

## 3. 회원 / 계정

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 3.3 | **회원 탈퇴 / 개인정보 처리** | 🟡 | M | - | soft delete/익명화 정책, 멤버십·정기배송 해지 연쇄 |

## 6. 알림 / 메시징

| # | 항목 | 우선순위 | 작업량 | 선행조건 | 메모 |
|---|------|:------:|:----:|------|------|
| 6.2 | **배송 추적 연동** | 🟢 | M | - | 택배사 API, 배송 상태 노출 |
| 6.3 | **웹 푸시** | 🟢 | L | 6.1 | |

## 7. 정책 선행 과제 (기획 오픈 이슈)

`docs/planning/README.md` 공통 오픈 이슈 중 미해결 항목. 구현 전 정책 확정 필요.

| # | 항목 | 우선순위 | 메모 |
|---|------|:------:|------|
| 7.1 | **배송비 모델** | 🔴 | 현재 전 상품 무료배송 전제. 멤버십 무료배송·새벽배송 추가요금의 기반 |
| 7.2 | **인센티브 어뷰징 방지 원칙** | 🟡 | 리퍼럴·리뷰 이벤트 착수 조건 |

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
| 외부 연동 키 | 1.2, 1.3, 1.4, 1.5 | Toss 테스트 키(클라이언트/시크릿) |
| 외부 연동 키 | (실발송만) | 6.1·3.1 코드는 완료 — 실제 메일 발송에만 SMTP 계정 필요(`MAIL_HOST` 등) |
| 외부 연동 키 | 6.2, 6.3 | 택배사 API 계약 / 웹푸시 VAPID |
| 정책 결정 | 7.1, 7.2 | 배송비 모델, 어뷰징 방지 원칙 |
| 정책 결정 | 3.3 | 탈퇴 시 soft delete vs 익명화 |
| 정책 결정 | 2.4 | 상품 Q&A 공개 문의 vs 비밀 문의(스키마·알림이 갈림), `docs/planning/` 기획서 선행 |
| 측정 결과 보류 | 8.3, 8.6, 8.8 | 현재 규모에서 이득이 확인되지 않음 |

## 추천 진행 순서

1. **결제 완성** — 1.2 Toss 위젯 → 1.3 PG 취소 연동 (Toss 테스트 키 필요)
2. **상품 완성도** — 업로드 저장소를 S3/영속 볼륨으로 교체(운영 배포 전)
4. **정책 확정 후** — 7.1 배송비 모델
