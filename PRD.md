# 이커머스 플랫폼 PRD

> Product Requirements Document
> 작성일: 2026-06-27
> 상태: Draft (v0.2) — 마켓플레이스/쿠폰·포인트/게스트 주문 확정 반영

## 1. 개요

### 1.1 목적
Kotlin + Spring Boot 기반 `spring-starter-kit` 위에 **B2C 이커머스 백엔드**를 구축한다.
스타터 킷이 이미 제공하는 인증/인가/감사로그/관리자 기반을 재사용하고, 그 위에 상품·주문·결제 등
이커머스 핵심 도메인을 확장한다.

### 1.2 스타터 킷에서 그대로 활용하는 것 (재구현 금지)
| 기능 | 활용 방식 |
|------|-----------|
| 세션 로그인 / OAuth2 소셜 로그인 | 회원 인증에 그대로 사용 |
| RBAC (User-Role-Permission) | `ROLE_CUSTOMER`, `ROLE_SELLER`, `ROLE_ADMIN` 으로 확장 |
| 공통 응답(`ApiResponse`) / 에러(`ErrorCode`, `BusinessException`) | 모든 신규 API에 동일 규약 적용 |
| 감사 로그(JSONB) | 주문/결제 등 민감 액션 추적에 활용 |
| 관리자 기능 골격 | 상품/주문/회원 관리 메뉴 확장 |
| BaseTimeEntity (Auditing) | 모든 신규 엔티티 상속 |
| Flyway 마이그레이션 | 신규 테이블 버전 관리 |

### 1.3 기술 스택 (스타터 킷 계승)
- Kotlin 1.9.25, Spring Boot 3.5.x, JVM 21
- Spring Web MVC / Security / Data JPA / OAuth2 Client
- Kotlin JDSL (동적 쿼리), PostgreSQL + Flyway
- JUnit5, MockK, SpringMockK, Testcontainers

## 2. 목표 및 비목표

### 2.1 목표 (MVP 범위)
1. 고객이 상품을 탐색·검색하고 장바구니에 담아 주문/결제할 수 있다.
2. 재고가 정확하게 차감되고, 동시 주문 시 초과 판매가 발생하지 않는다.
3. 판매자(또는 관리자)가 상품과 재고를 등록/관리할 수 있다.
4. 관리자가 주문·회원·상품을 조회·관리할 수 있다.
5. 결제는 외부 PG 연동을 가정하되, MVP는 **모의(Mock) 결제 어댑터**로 시작한다.

### 2.2 비목표 (MVP 제외, 추후 단계)
- 실제 PG사(토스/아임포트 등) 실연동 → MVP는 Mock PG. PG 어댑터 골격(`PaymentGateway` + Mock/Toss 스텁)은 구현, 실 승인/세금계산서는 추후
- 쿠폰 엔진 고도화(복합·중첩 할인 규칙) → MVP는 단순 할인 쿠폰 + 포인트
- 추천/검색 랭킹(ML), 리뷰·평점 상세
- 다국가/다통화, 멀티 창고 물류

## 3. 사용자 역할 (RBAC 확장)

> **마켓플레이스 모델**: 다수의 독립 판매자(Seller/Store)가 입점하여 각자 상품을 판매한다.
> 한 주문에 **여러 판매자의 상품**이 섞일 수 있으며, 주문은 판매자 단위 하위 주문으로 분리되어 정산·배송된다.

| 역할 | 설명 | 주요 권한 |
|------|------|-----------|
| (게스트) | 비회원 | 상품 조회, **localStorage 장바구니**, **게스트 주문/결제**(주문조회는 주문번호+연락처 검증) |
| `ROLE_CUSTOMER` | 일반 구매 고객 (회원가입 기본 역할) | 게스트 권한 + 서버 저장 장바구니, 본인 주문 조회, 쿠폰/포인트 사용 |
| `ROLE_SELLER` | 입점 판매자 | 본인 Store 상품 등록/수정, 본인 판매분 주문 조회/배송, 재고 관리 |
| `ROLE_ADMIN` | 운영 관리자 | 전체 상품/주문/회원/카테고리/셀러 관리, 쿠폰 발행, 환불 승인 |

> 게스트 주문: 비회원도 주문/결제 가능. 주문 조회는 **주문번호 + 주문 시 입력한 연락처/이메일**로 인증.
> 게스트는 포인트·쿠폰 미지원. 회원 전환 시 게스트 주문 연결은 추후 단계.

## 4. 도메인 모델 (핵심 엔티티)

```
Seller(Store) ── 입점 판매자
   │
Category (계층형)
Product ── ProductOption(SKU) ── Inventory(재고)   (Product는 Store에 속함)
   │
Cart ── CartItem ── (Product/Option 참조)          (회원 전용)
   │
Order ── SubOrder(판매자 단위) ── OrderItem ── (주문 시점 상품 스냅샷)
   │         │
   │      Shipment(배송) ── Address                (배송은 SubOrder 단위)
   │
Payment ── PaymentEvent(상태 이력)                 (Order 단위 1건)
   │
Coupon / IssuedCoupon ── 할인 쿠폰
PointAccount ── PointTransaction ── 적립/사용 이력   (회원 전용)
```

### 4.1 엔티티 요약
- **Seller(Store)**: 입점 판매자. User(`ROLE_SELLER`)와 연결, 상점명·정산정보·상태(입점심사/영업중/정지)
- **Category**: 계층형(부모-자식), 상품 분류 (플랫폼 공통)
- **Product**: 상품 기본 정보(이름, 설명, 대표가격, 상태 DRAFT/ON_SALE/SOLD_OUT/HIDDEN). **소속 Store 필수**
- **ProductOption (SKU)**: 옵션 단위 판매(예: 색상/사이즈), 옵션별 가격·재고
- **Inventory**: SKU별 가용 재고/예약 재고. 재고 차감의 **단일 진실 공급원**
- **Cart / CartItem**: 회원당 1개 장바구니, 항목별 수량 (게스트 미지원)
- **Order**: 주문 헤더. 여러 판매자 상품을 한 번에 결제 → **SubOrder로 분리**. 게스트/회원 주문 모두 표현(주문자 연락처 보관)
- **SubOrder**: 판매자 단위 하위 주문. 배송·정산·상태 전이의 단위
- **OrderItem**: 주문 시점 가격/상품명/옵션 **스냅샷** 보관(원본 변경 무관). SubOrder에 소속
- **Payment / PaymentEvent**: Order 단위 결제 본체 + 상태 전이 이력(READY/PAID/CANCELED/FAILED). 금액에 쿠폰/포인트 차감 반영
- **Shipment / Address**: 배송 정보, 배송지. **SubOrder 단위**로 송장 발급
- **Coupon / IssuedCoupon**: 쿠폰 정의(할인율/정액, 최소주문금액, 유효기간) + 회원에게 발급된 인스턴스(사용여부)
- **PointAccount / PointTransaction**: 회원 포인트 잔액 + 적립/사용/만료 원장(이력 기반, 잔액은 합산 검증)

## 5. 핵심 기능 요구사항

### 5.1 상품 (Product)
- **고객**: 카테고리/키워드 검색, 상품 상세, 옵션·재고 노출 (Kotlin JDSL 동적 검색)
- **판매자/관리자**: 상품 CRUD, 옵션·재고 등록, 판매 상태 변경
- 품절 상품은 노출하되 구매 차단

### 5.2 장바구니 (Cart)
- **회원**: 서버에 저장(회원당 1개). 항목 추가/수량변경/삭제/비우기. 담는 시점 재고/판매상태 검증
- **게스트**: **localStorage(클라이언트)** 에 `{optionId, quantity}[]` 보관 → 서버 무상태.
  표시·결제 직전 검증을 위해 서버는 **계산 전용 엔드포인트**(`POST /api/cart/guest`)로 현재가·재고·구매가능 여부·합계를 돌려준다(저장하지 않음)
- 가격은 항상 조회 시점 현재가 기준(스냅샷 아님). 결제 직전 재검증
- 로그인 시 localStorage 장바구니 → 서버 장바구니 병합은 추후 단계(merge API)

### 5.3 주문 (Order) — 멀티셀러
- 장바구니/직접 → 주문 생성 시: 재고 검증 → **재고 예약/차감** → 주문 확정
- 한 주문에 여러 판매자 상품이 섞이면 **SubOrder(판매자 단위)로 분리**. 결제는 Order 단위 1건, 배송·취소·정산은 SubOrder 단위
- 상태 전이(SubOrder 기준): `CREATED → PAID → PREPARING → SHIPPED → DELIVERED` / `CANCELED`
- 주문 항목은 생성 시점 가격·상품명·옵션 스냅샷
- **회원**: 본인 주문 목록/상세 조회, 전체 취소(배송 전) + **SubOrder 단위 부분 취소**(`POST /api/orders/sub-orders/{id}/cancel`). 각 SubOrder에 결제금액 비례 배분(payable_share) → 부분 환불, 전체 취소 완료 시 쿠폰/포인트 복원·적립 회수·결제 취소
- **게스트**: 주문번호 + 연락처/이메일로 단건 조회·취소
- 금액 계산: 상품합계 − 쿠폰할인 − 포인트사용 = 최종 결제금액 (전부 서버 계산)

### 5.4 재고 (Inventory) — 정합성 핵심
- 동시 주문에도 **초과 판매 금지**가 최우선 요구사항
- 전략: DB 비관적 락(`SELECT ... FOR UPDATE`) 또는 원자적 `UPDATE ... WHERE stock >= qty`
- 주문 취소/결제 실패 시 재고 복원
- 재고 변동은 감사 가능해야 함(로그/이력)

### 5.5 결제 (Payment)
- MVP: **Mock PG 어댑터** (결제요청 → 가상 승인/실패 콜백)
- 인터페이스(`PaymentGateway`)로 추상화하여 추후 실 PG 교체 가능
- 결제 성공 시 주문 PAID 전이 + 재고 확정, 실패 시 주문/재고 롤백
- 멱등성: 동일 주문 중복 결제 방지

### 5.6 배송 (Shipment) — SubOrder 단위
- 배송지 등록/선택, SubOrder에 배송지 연결 (한 주문이 여러 판매자면 판매자별 개별 배송)
- **판매자**: 본인 SubOrder에 송장 등록 → SHIPPED 전이, 배송 상태 갱신

### 5.7 쿠폰 / 포인트
- **쿠폰**: 관리자(또는 판매자)가 쿠폰 발행 → 회원이 수령(IssuedCoupon). 정률/정액, 최소주문금액, 유효기간, 1회용
  - 주문 시 1개 적용(MVP는 중첩 불가). 결제 실패/주문 취소 시 미사용 상태로 복원
- **포인트**: 회원별 PointAccount. 적립(주문 확정 시 일정 비율) / 사용(결제 시 차감) / **만료**
  - **lot 기반 FIFO**: 적립마다 만료일을 가진 lot 생성, 사용은 만료 임박 순 차감, 만료는 lot 잔여만 소멸(이중 차감 방지). 원장(PointTransaction)으로 음수 방지
  - 주문 취소 시 사용 포인트 환원(새 lot), 적립 포인트 회수(해당 주문 lot 잔여)
  - **적립률·유효기간 모두 관리자 설정값**(`point_policies`: earn_rate_bp 기본 100=1%, expiry_days 기본 365). `GET/PATCH /api/admin/point-policy` 부분 업데이트
  - 만료 트리거: `POST /api/admin/points/expire`(수동) — 스케줄러 주기 실행도 가능
- 게스트는 쿠폰/포인트 미지원

### 5.8 판매자 (Seller) — 입점
- 판매자 입점 신청 → 관리자 심사/승인 → `ROLE_SELLER` 부여 + Store 활성화
- 본인 Store의 상품/옵션/재고 CRUD, 본인 판매분 SubOrder 조회·배송 처리

### 5.10 정산 (Settlement) — 셀러 정산
- 미정산 SubOrder(취소 제외, 상태 PAID/PREPARING/SHIPPED/DELIVERED, `settlement_id IS NULL`)를 **판매자 단위로 집계**해 정산서 생성
- 판매액(∑subtotal) − 플랫폼 수수료 = 지급액. 같은 SubOrder는 `settlement_id`로 표시해 **재정산 방지**
- **수수료율은 관리자 설정값**(`settlement_policies.commission_rate_bp`, 기본 1000=10%). `GET/PATCH /api/admin/settlements/policy`
- 관리자: `POST /api/admin/settlements`(생성), `PATCH /api/admin/settlements/{id}/pay`(지급 완료). 판매자: `GET /api/seller/settlements`(본인 정산 내역)

### 5.9 관리자 (Admin) — 스타터 킷 관리자 확장
- 셀러 입점 심사/관리, 상품/카테고리 관리, 전체 주문 검색(상태/기간/회원/판매자), 환불 처리, 쿠폰 발행
- 기존 회원/권한/감사로그 검색 기능 그대로 유지

## 6. API 설계 개요 (공통 응답 규약 준수)

> 모든 응답: `{ success, code, message, data }` / 상태변경 요청은 CSRF 토큰 필요

### 공개/고객 API
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/products?keyword=&categoryId=&sellerId=&page=` | 상품 검색 | 불필요 |
| GET | `/api/products/{id}` | 상품 상세 | 불필요 |
| GET | `/api/cart` | 내 장바구니(서버 저장) | 회원 |
| POST | `/api/cart/guest` | 게스트 장바구니 계산/검증(localStorage 동반, 무상태) | 불필요 |
| POST | `/api/cart/items` | 장바구니 담기 | 회원 |
| PATCH | `/api/cart/items/{id}` | 수량 변경 | 회원 |
| DELETE | `/api/cart/items/{id}` | 항목 삭제 | 회원 |
| POST | `/api/orders` | 주문 생성(쿠폰/포인트 적용) | 회원 |
| POST | `/api/orders/guest` | 게스트 주문 생성 | 불필요 |
| GET | `/api/orders` | 내 주문 목록 | 회원 |
| GET | `/api/orders/{id}` | 주문 상세 | 회원 |
| POST | `/api/orders/guest/lookup` | 게스트 주문 조회(주문번호+연락처) | 불필요 |
| POST | `/api/orders/{id}/cancel` | 주문/하위주문 취소 | 회원 |
| POST | `/api/payments/{orderId}` | 결제 요청 | 회원/게스트 |
| POST | `/api/payments/callback` | (Mock) PG 콜백 | 시스템 |
| GET | `/api/me/coupons` | 내 쿠폰 목록 | 회원 |
| GET | `/api/me/points` | 내 포인트 잔액/이력 | 회원 |

### 판매자 API (`ROLE_SELLER`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/seller/apply` | 입점 신청 |
| POST | `/api/seller/products` | 상품 등록 |
| PUT | `/api/seller/products/{id}` | 상품 수정 |
| PATCH | `/api/seller/products/{id}/stock` | 재고 조정 |
| GET | `/api/seller/orders?status=` | 본인 판매분 SubOrder 조회 |
| POST | `/api/seller/orders/{subOrderId}/ship` | 송장 등록/발송 |

### 관리자 API (`ROLE_ADMIN`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/sellers?status=` | 입점 신청/셀러 검색 |
| PATCH | `/api/admin/sellers/{id}/approve` | 입점 승인/거절 |
| GET | `/api/admin/orders?status=&sellerId=&from=&to=` | 전체 주문 검색 |
| POST | `/api/admin/orders/{id}/refund` | 환불 처리 |
| POST | `/api/admin/categories` | 카테고리 등록 |
| POST | `/api/admin/coupons` | 쿠폰 발행 |
| POST | `/api/admin/settlements` | 정산서 생성(판매자별 집계) |
| PATCH | `/api/admin/settlements/{id}/pay` | 정산 지급 완료 |
| GET/PATCH | `/api/admin/settlements/policy` | 수수료율 정책 조회/변경 |

## 7. 비기능 요구사항
- **정합성**: 재고/결제는 트랜잭션 경계 명확화, 초과판매 0건
- **보안**: 본인 리소스만 접근(주문/장바구니 소유권 검증), 가격은 항상 서버 계산(클라이언트 금액 신뢰 금지)
- **관측성**: 주문/결제 핵심 액션 감사 로그 기록(스타터 킷 AuditLog 활용)
- **테스트**: 재고 동시성, 주문-결제 상태 전이는 통합 테스트(Testcontainers) 필수
- **마이그레이션**: 모든 스키마 변경은 Flyway 버전 스크립트로

## 8. 개발 단계 (로드맵)

| 단계 | 내용 | 산출물 |
|------|------|--------|
| Phase 0 | PRD 확정, RBAC 역할 확장, 셀러/ERD/마이그레이션 설계 | 본 문서, Flyway 베이스 |
| Phase 1 | 셀러(Store) + 카테고리·상품·옵션·재고 도메인 + 조회 API | 상품 카탈로그 |
| Phase 2 | 장바구니 | 장바구니 API |
| Phase 3 | 주문 생성(멀티셀러 SubOrder) + 재고 차감(동시성) | 주문 API + 동시성 테스트 |
| Phase 4 | 쿠폰/포인트 적용 + 결제(Mock PG) + 상태 전이 | 결제 플로우 |
| Phase 5 ✅ | 배송(SubOrder 단위 송장) + 게스트 주문/조회 + 배송지 + 결제후 취소/환불 | 배송/CS |
| Phase 6 ✅ | 판매자 백오피스(입점신청·상품/재고 CRUD) + 관리자(셀러 심사·카테고리·쿠폰 발행·주문검색·환불) | 운영 백오피스 |
| 추가 ✅ | 미룬 항목: SubOrder 부분 취소/환불 · 포인트 만료(lot FIFO) · PG 어댑터 골격 · **셀러 정산(수수료율 관리자 설정)** | 정산/CS 보강 |
| Phase 7 ✅ | 테스트 보강(정산 엣지 케이스) / 문서화(README 이커머스 개편) — 75개 테스트 그린 | 안정화 |

## 9. 핵심 결정 사항 (확정)
1. **판매자 모델**: ✅ **마켓플레이스(멀티 셀러)**. 주문은 SubOrder로 판매자 단위 분리
2. **쿠폰/포인트**: ✅ **MVP 포함**. 단순 할인 쿠폰 + 포인트 적립/사용(중첩·복합 규칙 제외)
3. **게스트 주문**: ✅ **허용**. 주문번호+연락처 인증, 장바구니/쿠폰/포인트는 회원 전용
4. **재고 동시성 전략**: ✅ **원자적 UPDATE** (`UPDATE inventories SET reserved = reserved + :qty WHERE option_id = :id AND quantity - reserved >= :qty`). 영향 행 0이면 재고 부족으로 처리. 락 경합·데드락이 적고 MVP에 단순
5. **옵션 모델**: MVP는 **SKU 단위 단순화**(다축 옵션 조합 제외) 권장
6. **결제 멱등성 키** 설계: 주문ID 기반 멱등 처리

## 10. 미해결 질문 (Open Questions)
- [x] 셀러 정산 모델 → **구현 완료**. 수수료율은 관리자 설정값(`settlement_policies`, 기본 10%), 정산은 관리자 수동 트리거(`POST /api/admin/settlements`). 정산 주기 스케줄러는 추후
- [x] 포인트 적립률 → **관리자 설정값**(`point_policies`, 기본 1%). 유효기간/만료 정책 수치는 추후
- [x] 게스트 주문의 회원 전환 시 주문 연결 → **구현 완료**. `POST /api/orders/claim`(주문번호+연락처 확인 후 소유자 연결)
- [x] 실 PG사 타깃 → **토스페이먼츠**. `TossPaymentGateway` 로 confirm 연동 완성(paymentKey 수신·금액 위변조 거절), `payment.gateway=toss` 로 전환
