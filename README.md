# E-Commerce Platform (B2C 마켓플레이스)

Kotlin + Spring Boot 백엔드와 React 고객 스토어프론트로 구성된 **B2C 마켓플레이스 이커머스 풀스택 애플리케이션**.
다수 판매자가 입점하고, 고객(또는 게스트)이 상품을 탐색·검색·주문·결제하며, 쿠폰/포인트·배송·환불·셀러 정산까지 지원한다.
검증된 스타터 킷의 세션 인증/RBAC/감사로그/공통응답을 토대로 그 위에 이커머스 도메인을 확장했다.

- **백엔드**: Kotlin · Spring Boot 3.5 · PostgreSQL/Flyway · 세션 인증/RBAC — `src/`
- **프론트엔드**: React 19 · Vite · TypeScript · Tailwind CSS — [`frontend/`](frontend/) (고객 스토어프론트 SPA)

> 제품 요구사항·설계 결정의 전체 맥락은 [`PRD.md`](PRD.md), 기능 기획·백로그는 [`docs/planning/`](docs/planning/), 내부 구현은 [`docs/SERVER_ARCHITECTURE.md`](docs/SERVER_ARCHITECTURE.md) 참조.

## 이커머스 핵심 개념
- **마켓플레이스(멀티 셀러)**: 한 주문에 여러 판매자 상품이 섞이면 **SubOrder(판매자 단위)** 로 분리된다. 결제는 Order 단위 1건, 배송·취소·정산은 SubOrder 단위.
- **재고 정합성**: 초과 판매 0건이 최우선. **원자적 UPDATE**(`UPDATE ... WHERE quantity - reserved >= qty`)로 동시 주문에도 오버셀링을 막는다.
- **금액은 전부 서버 계산**: 상품합계 − 쿠폰할인 − 포인트사용 = 결제금액. 클라이언트 금액은 신뢰하지 않는다.
- **게스트 주문**: 비회원도 주문/결제 가능(주문번호+연락처로 조회). 장바구니·쿠폰·포인트는 회원 전용.
- **관리자 설정값**: 포인트 적립률/유효기간, 정산 수수료율은 코드 상수가 아니라 DB 정책 행으로 런타임 변경한다.

---

## 아키텍처

### 시스템 구성
세션 기반 인증을 쓰는 모놀리식 백엔드 + 별도 SPA. 둘 다 같은 오리진(개발 시 Vite 프록시)으로 묶여 쿠키 세션/CSRF가 자연스럽게 동작한다.

```
┌──────────────────────────┐        ┌──────────────────────────────────────┐
│  React 스토어프론트 (SPA) │        │     Spring Boot (모놀리식 API)         │
│  Vite · TS · Tailwind     │        │                                        │
│                           │  HTTP  │  SecurityFilterChain                   │
│  fetch 클라이언트         │ ─────► │   ├ CSRF(쿠키토큰) · 세션 인증/RBAC     │
│   · 세션 쿠키(credentials)│  JSON  │   └ AuditLogFilter(@Async JSONB)       │
│   · XSRF-TOKEN → 헤더     │ ◄───── │  @RestController  (도메인별)            │
│                           │        │  Service (트랜잭션 경계·금액 계산)      │
│  AuthContext · Protected  │        │  Repository (Spring Data JPA + JDSL)   │
│  Route(역할 게이트)        │        └───────────────┬────────────────────────┘
└──────────────────────────┘                        │ JPA / 원자적 UPDATE
                                                     ▼
                                     ┌──────────────────────────────────────┐
                                     │  PostgreSQL  (스키마: Flyway V1~V25)   │
                                     │  audit_logs JSONB(GIN) · 정책행 런타임  │
                                     └──────────────────────────────────────┘
                            결제: PaymentGateway 추상화 → Mock / Toss(@ConditionalOnProperty)
                            스케줄러: 정산·포인트만료·멤버십빌링·로열티등급·카트리마인드 배치(@Scheduled, 조건부 활성화)
                            알림: 범용 인앱 알림함(Notification) + @Async·AFTER_COMMIT 이벤트(재입고·가격인하 등)
```

### 계층 구조 (백엔드)
도메인별 수직 슬라이스. 각 도메인은 `Controller → Service → Repository → Entity` 로 일관된다.

```
Controller   요청/응답 DTO 변환, 인가(URL 규칙 + @PreAuthorize 이중 방어)
   ↓
Service      트랜잭션 경계, 비즈니스 규칙(금액 서버계산·재고 원자성·상태 전이)
   ↓
Repository   Spring Data JPA + Kotlin JDSL(동적 검색) + 네이티브 원자적 UPDATE
   ↓
Entity       BaseTimeEntity(JPA Auditing), 화폐=Long(KRW), 비율=basis point
```

### 핵심 도메인 모델
```
User ─< Seller(Store) ─< Product ─< ProductOption ─ Inventory(quantity/reserved)
                                         │
Order ─< SubOrder(판매자 단위) ─< OrderItem ┘     ← 멀티셀러 주문 분리
  │         │
  │         └─ Shipment(송장) · Settlement(정산, 수수료 차감)
  └─ Payment ─< PaymentEvent        Coupon/IssuedCoupon · PointAccount/PointLot(FIFO)
```

### 주문 라이프사이클
```
장바구니 → 주문생성(SubOrder 분리 + 재고 reserved 원자적 차감 + 쿠폰/포인트 적용)
        → 결제(멱등, 금액 서버검증) → 판매자 송장등록(SHIPPED) → 배송완료(DELIVERED)
        → (정산 집계·수수료 차감) ─ 지급
        취소/환불: Order 전체 또는 SubOrder 부분 단위로 재고 복원 + 쿠폰/포인트 복원
```

---

## (기반) Spring Starter Kit
이 프로젝트의 토대인 스타터 킷은 세션 로그인 / OAuth2 소셜 로그인 / RBAC 권한 / 공통 응답·에러 / 감사 로그 / 관리자 기능을 제공한다.

## 기술 스택
**백엔드**
- **언어/빌드**: Kotlin, Gradle(Kotlin DSL), JVM 21 toolchain
- **프레임워크**: Spring Boot 3.5.x (Web MVC, Security, Data JPA, OAuth2 Client, Actuator, Validation)
- **쿼리**: [Kotlin JDSL](https://github.com/line/kotlin-jdsl) — 타입 안전한 동적 쿼리
- **DB**: PostgreSQL (로컬/운영), Flyway 마이그레이션
- **테스트**: JUnit5, MockK, SpringMockK, Testcontainers, spring-security-test

**프론트엔드** (`frontend/`)
- **React 19** + **Vite** + **TypeScript** + **Tailwind CSS v4**
- **react-router-dom v7** 라우팅, fetch 기반 API 클라이언트(CSRF 자동 첨부)
- 세션 기반 `AuthContext` + `ProtectedRoute`(역할 게이트), 게스트 장바구니는 localStorage

## 구현 현황 (이커머스)
| 단계 | 내용 | 상태 |
|------|------|------|
| Phase 1 | 셀러(Store) + 카테고리·상품·옵션·재고 + 상품 조회 API | ✅ |
| Phase 2 | 장바구니(회원 서버저장 + 게스트 무상태 계산 API) | ✅ |
| Phase 3 | 주문 생성(멀티셀러 SubOrder 분리) + 재고 차감(원자적 UPDATE 동시성) | ✅ |
| Phase 4 | 쿠폰/포인트 적용 + 결제(Mock PG) + 상태 전이 | ✅ |
| Phase 5 | 배송(SubOrder 단위 송장) + 게스트 주문/조회 + 배송지 + 결제후 취소/환불 | ✅ |
| Phase 6 | 판매자 백오피스 + 관리자 운영(셀러 심사·카테고리·쿠폰·주문검색·환불) | ✅ |
| 추가 | SubOrder 부분 취소/환불 · 포인트 만료(lot FIFO) · PG 어댑터 골격 · 셀러 정산 | ✅ |
| Phase 7 | 테스트 보강 / 문서화 | ✅ |
| Phase 8 | 고객 스토어프론트(React SPA) + 판매자/관리자 백오피스 | ✅ |
| Phase 9 | 상품 검색(키워드·카테고리·판매자) + 판매량 기반 인기 상품 | ✅ |
| 커머스 스위트 | 상품 리뷰/포토리뷰 + 재입고 알림 + 범용 인앱 알림함(Notification) | ✅ |
| 커머스 스위트 | 기획전/컬렉션 큐레이션(promotion) + 플래시세일/타임딜(flashsale) | ✅ |
| 커머스 스위트 | 배송 권역·배송 슬롯 예약(delivery) + 새벽배송 플래그 | ✅ |
| 커머스 스위트 | 유료 멤버십 + 정기결제 빌링(membership/billing) + 정기배송 구독(subscription) | ✅ |
| 커머스 스위트 | 선물하기/기프트 클레임(gift) | ✅ |
| 리텐션 | 위시리스트 + 가격 인하 알림(wishlist) | ✅ |
| 리텐션 | 누적구매 로열티 등급 + 등급 승급 쿠폰 자동발급(loyalty) | ✅ |
| 리텐션 | 장바구니 이탈 리마인드(cart, 알림 전용) | ✅ |

> 기반 스타터 킷 단계(공통 응답/에러, RBAC, OAuth2, 감사로그, 관리자)는 모두 완료된 상태에서 출발한다.
> 커머스 스위트·리텐션 기능의 "무엇을·왜"는 [`docs/planning/`](docs/planning/) 기획 문서를 참조.

## 패키지 구조
```
com.example.starter
├── common/        공통 응답·예외·엔티티·설정·부트스트랩
│   ├── response/  ApiResponse, FieldErrorDetail
│   ├── exception/ ErrorCode, BusinessException, GlobalExceptionHandler
│   ├── entity/    BaseTimeEntity (JPA Auditing)
│   ├── config/    SecurityConfig, JpaConfig, JdslConfig
│   └── bootstrap/ AdminBootstrap (local 전용 관리자 시드)
├── security/      UserDetails, 인증/인가 JSON 핸들러, CSRF 필터
└── domain/
    ├── user/      User·Role·Permission 엔티티/리포지토리   (스타터 킷)
    ├── auth/      회원가입·로그인·로그아웃·내정보 API        (스타터 킷)
    ├── admin/     사용자/권한/감사로그 관리 + PageResponse  (스타터 킷)
    ├── seller/    입점 신청·심사, 본인 SubOrder 조회/송장
    ├── catalog/   카테고리·상품·옵션·재고(원자적 차감), 상품 검색, 리뷰 요약·새벽배송 플래그
    ├── cart/      회원 장바구니 + 게스트 무상태 계산 API, 이탈 리마인드 배치
    ├── order/     Order·SubOrder·OrderItem, 생성/취소/부분취소, 게스트 주문
    ├── payment/   Payment·PaymentEvent, PaymentGateway(Mock/Toss), 멱등 결제
    ├── coupon/    Coupon·IssuedCoupon, 관리자 발행
    ├── point/     PointAccount·PointLot(FIFO)·PointTransaction, 적립/사용/만료, 정책
    ├── member/    내 쿠폰/포인트 조회(MeController)
    ├── settlement/ 셀러 정산(판매자별 집계·수수료 차감), 수수료 정책
    ├── review/    상품 리뷰/포토리뷰, 구매 인증, 리뷰 요약(상품 상세 노출)
    ├── restock/   재입고 알림(옵션 단위 임계값 감지 → 인앱 알림)
    ├── notification/ 범용 인앱 알림함(NotificationType enum), @Async 발송
    ├── promotion/ 기획전/컬렉션 큐레이션(MD 편집 진열)
    ├── flashsale/ 플래시세일/타임딜(한정특가·수량, 재고 원자성 재사용)
    ├── delivery/  배송 권역·배송 슬롯 예약(새벽/시간대), 새벽배송
    ├── membership/ 유료 멤버십(등급/혜택), 구독 상태
    ├── billing/   정기결제 빌링키 발급·저장·청구(멤버십/정기배송 공통 기반)
    ├── subscription/ 정기배송 구독(반복 주문 자동화)
    ├── gift/      선물하기(배송지 없는 결제) + 수령자 기프트 클레임
    ├── wishlist/  위시리스트(찜) + 가격 인하 알림(Product.basePrice 하락 감지)
    └── loyalty/   누적구매 로열티 등급(최근 12개월 순구매액) + 승급 쿠폰 자동발급
```

> 화폐는 KRW 정수(`Long`), 비율은 basis point(100 = 1%, 1000 = 10%)로 다룬다.
> 스키마 변경은 전부 Flyway 버전 스크립트(`src/main/resources/db/migration/V*.sql`).

## 사전 준비
- JDK 21 (Gradle 런처가 JDK 21을 사용해야 함 — 아래 "환경 주의" 참고)
- Docker (PostgreSQL 실행용)

## 실행
```bash
# 1) PostgreSQL 기동
docker compose up -d

# 2) 애플리케이션 실행 (기본 프로파일: local)
./gradlew bootRun
```
- 기본 포트: `8080` (이미 사용 중이면 `--args='--server.port=8081'`)
- local 프로파일에서 관리자 계정 자동 생성: `admin@example.com` / `Admin1234!`

### 주요 API (자체 인증)
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| POST | `/api/auth/signup` | 회원가입 | 불필요 |
| POST | `/api/auth/login` | 로그인(세션 생성) | 불필요 |
| POST | `/api/auth/logout` | 로그아웃 | 세션 |
| GET | `/api/auth/me` | 내 정보(DB 역할로 세션 권한 갱신) | 세션 |
| PATCH | `/api/auth/password` | 비밀번호 변경 | 세션 |
| POST | `/api/auth/password-reset/request` · `/confirm` | 비밀번호 분실 재설정(메일 토큰) | 불필요 |

> **CSRF**: 세션 기반 + SPA 친화로 쿠키 토큰 방식을 쓴다. 클라이언트는 GET 요청 후 받은
> `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 실어 상태 변경 요청을 보낸다.

### 소셜 로그인 (OAuth2)
구글/네이버/카카오를 지원하며, **이메일 기준으로 자체 계정과 연동**된다(미존재 시 자동 회원가입).
시크릿이 없으면 소셜 로그인은 비활성(앱은 정상 부팅)이며, 사용하려면 `oauth` 프로파일을 켜고 환경변수를 주입한다.
```bash
export SPRING_PROFILES_ACTIVE=local,oauth
export GOOGLE_CLIENT_ID=... GOOGLE_CLIENT_SECRET=...
export NAVER_CLIENT_ID=...  NAVER_CLIENT_SECRET=...
export KAKAO_CLIENT_ID=...  KAKAO_CLIENT_SECRET=...
./gradlew bootRun
```
- 로그인 시작: `GET /oauth2/authorization/{google|naver|kakao}`
- 콜백: `/login/oauth2/code/{provider}` → 성공 시 `app.oauth2.success-redirect-uri` 로 이동
- 한 사용자가 여러 소셜 계정을 연동 가능(`oauth_accounts` 테이블)

## 프론트엔드 (고객 스토어프론트)
React + Vite SPA. 백엔드와 **세션 쿠키 + CSRF 쿠키토큰**으로 통신하며, fetch 래퍼가 상태 변경 요청에 `X-XSRF-TOKEN` 헤더를 자동 첨부한다.

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173 (/api 요청은 :8080 백엔드로 프록시)
```

주요 화면:
- **고객**: 상품 목록(카테고리 필터·인기 상품)·상세(리뷰·찜), 장바구니, 체크아웃(쿠폰/포인트·배송슬롯 적용), 게스트 주문/조회, 선물하기/기프트 클레임, 마이페이지(주문·쿠폰·포인트·찜한 상품·내 등급·멤버십·정기배송·알림함)
- **판매자 백오피스**: 상품 관리, 주문/송장, 정산 내역, 플래시세일 신청
- **관리자 백오피스**: 셀러 심사, 주문/환불, 쿠폰 발행, 정산 관리, 컬렉션·플래시세일 편성, 배송 권역/슬롯, 멤버십, 리뷰 검수, 로열티 등급
- **게스트 → 회원 전환**: 비회원 장바구니(localStorage)는 로그인 시 서버 장바구니로 병합(`/api/cart/merge`)

```
frontend/src
├── api/         client(fetch+CSRF) · endpoints · types
├── auth/        AuthContext(세션) · 게스트 장바구니 병합
├── hooks/       useWishlist(찜 상태 컨텍스트) 등
├── components/  Layout · ProtectedRoute · ProductCard · OrderView · WishlistButton · NotificationBell
└── pages/       고객 화면(찜/등급/멤버십/알림함/선물하기 포함) + seller/* + admin/* 백오피스
```

> UI 리디자인(shadcn/ui + Tailwind v4 토큰, 라이트/다크)은 [`docs/design/storefront-ui-spec.md`](docs/design/storefront-ui-spec.md) 참조.

## 이커머스 API
> 모든 응답은 공통 규약 `{ success, code, message, data }`. 상태 변경 요청은 CSRF 토큰 필요.
> 아래 표는 **핵심 흐름만** 요약한다. 전체 목록·스키마는 앱 기동 후 `/swagger-ui/index.html`(prod 비활성)에서 본다.

### 공개/고객 API
| 메서드 | 경로 | 설명 | 인증 |
|--------|------|------|------|
| GET | `/api/products?keyword=&categoryId=&sellerId=&page=` | 상품 검색(JDSL 동적) | 불필요 |
| GET | `/api/products/{id}` | 상품 상세 | 불필요 |
| GET | `/api/cart` | 내 장바구니(서버 저장) | 회원 |
| POST | `/api/cart/guest` | 게스트 장바구니 계산/검증(무상태) | 불필요 |
| POST | `/api/cart/merge` | 로그인 시 게스트 localStorage 장바구니 병합 | 회원 |
| POST | `/api/cart/items` | 장바구니 담기 | 회원 |
| PATCH | `/api/cart/items/{id}` | 수량 변경 | 회원 |
| DELETE | `/api/cart/items/{id}` | 항목 삭제 | 회원 |
| POST | `/api/orders` | 주문 생성(쿠폰/포인트 적용) | 회원 |
| POST | `/api/orders/guest` | 게스트 주문 생성 | 불필요 |
| GET | `/api/orders` · `/api/orders/{id}` | 내 주문 목록/상세 | 회원 |
| POST | `/api/orders/guest/lookup` | 게스트 주문 조회(주문번호+연락처) | 불필요 |
| POST | `/api/orders/claim` | 게스트 주문을 회원 계정에 연결(주문번호+연락처) | 회원 |
| POST | `/api/orders/{id}/cancel` | 주문 전체 취소 | 회원 |
| POST | `/api/orders/sub-orders/{id}/cancel` | SubOrder 부분 취소/환불 | 회원 |
| POST | `/api/payments/{orderId}` | 결제 요청(멱등, 실 PG는 `paymentKey` 동반) | 회원 |
| POST | `/api/payments/guest` | 비회원 결제(주문번호+연락처 확인) | 불필요 |
| POST | `/api/payments/webhook/toss` | 토스 결제 웹훅(PG 재조회로 검증, CSRF 제외) | 불필요 |
| GET · POST · PUT · DELETE | `/api/me/addresses` | 배송지 주소록(기본 배송지 지정 포함) | 회원 |
| POST | `/api/uploads` | 이미지 업로드(상품·리뷰 사진) | 회원 |
| GET | `/api/me/coupons` · `/api/me/points` | 내 쿠폰/포인트 | 회원 |
| GET | `/api/collections` · `/api/collections/{id}` | 기획전/컬렉션 목록·상세 | 불필요 |
| GET | `/api/flash-sales` · `/api/flash-sales/{id}` | 진행/예정 플래시세일 | 불필요 |
| GET · POST | `/api/products/{id}/reviews` | 상품 리뷰 조회 / 작성(구매 인증) | 조회 불필요·작성 회원 |
| POST · DELETE | `/api/products/options/{optionId}/restock-alerts` | 재입고 알림 신청/해제 | 회원 |
| POST · DELETE · GET | `/api/me/wishlist` · `/api/me/wishlist/{productId}` | 위시리스트 담기/빼기/목록(가격 인하 배지) | 회원 |
| GET | `/api/me/loyalty-tier` | 내 로열티 등급·다음 등급까지 남은 금액 | 회원 |
| GET · PATCH | `/api/me/notifications` · `/api/me/notifications/{id}/read` | 인앱 알림함 조회·읽음(재입고·가격인하·카트리마인드 등) | 회원 |
| GET · POST | `/api/me/membership` | 내 멤버십 조회 / 구독(빌링키) | 회원 |
| GET | `/api/delivery-slots?regionId=&date=` | 배송 권역·슬롯 예약 가능 조회 | 불필요 |
| POST · GET | `/api/gift` · `/api/gift/{token}/claim` | 선물 주문 생성 / 수령자 기프트 클레임(토큰) | 회원 |
| GET · POST | `/api/me/delivery-subscriptions` | 정기배송 구독 조회/생성(일시정지·건너뛰기·재개) | 회원 |

### 판매자 API (`ROLE_SELLER`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/seller/apply` | 입점 신청(일반 회원 허용) |
| POST · PUT | `/api/seller/products` · `/api/seller/products/{id}` | 상품 등록/수정 |
| PATCH | `/api/seller/products/{id}/stock` | 재고 조정(절대값) |
| GET | `/api/seller/orders?status=` | 본인 판매분 SubOrder 조회 |
| POST | `/api/seller/orders/{subOrderId}/ship` | 송장 등록 → SHIPPED |
| GET | `/api/seller/settlements` | 본인 상점 정산 내역 |
| GET · POST | `/api/seller/flash-sales` | 본인 상품 플래시세일 신청/조회 |

### 관리자 API (`ROLE_ADMIN`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET · PATCH | `/api/admin/sellers?status=` · `/api/admin/sellers/{id}/approve` | 입점 심사/승인 |
| GET | `/api/admin/orders?status=&sellerId=&from=&to=` | 전체 주문 검색(JDSL 동적) |
| POST | `/api/admin/orders/{id}/refund` | 환불 처리(배송 후 반품 포함) |
| POST | `/api/admin/categories` | 카테고리 등록 |
| POST | `/api/admin/coupons` | 쿠폰 발행 + 회원 지급 |
| GET · PATCH | `/api/admin/point-policy` | 적립률·유효기간 정책 |
| POST | `/api/admin/points/expire` | 포인트 만료 트리거 |
| POST · PATCH | `/api/admin/settlements` · `/api/admin/settlements/{id}/pay` | 정산 생성/지급 |
| GET · PATCH | `/api/admin/settlements/policy` | 수수료율 정책 |
| GET · POST | `/api/admin/collections` | 기획전/컬렉션 편성·상품 배치 |
| GET · POST | `/api/admin/flash-sales` | 플래시세일 승인/편성 |
| GET · POST | `/api/admin/delivery-regions` · `/api/admin/delivery-slots` | 배송 권역·슬롯 운영 |
| GET · POST | `/api/admin/memberships` | 멤버십 플랜/구독 관리 |
| GET · PATCH | `/api/admin/reviews` · `/api/admin/review-policy` | 리뷰 검수·신고 처리, 리뷰 적립 정책 |
| GET | `/api/admin/loyalty-tiers?tier=` | 로열티 등급 조회 |
| POST | `/api/admin/loyalty-tiers/recalculate/run` | 로열티 등급 재계산 배치(수동) |
| POST | `/api/admin/cart-reminders/run` | 장바구니 이탈 리마인드 발송 배치(수동) |
| GET · POST | `/api/admin/gift-claims` · `/api/admin/delivery-subscriptions` | 선물 클레임·정기배송 운영 |

## 공통 응답 형태
```jsonc
// 성공
{ "success": true, "code": "SUCCESS", "message": "...", "data": { ... } }
// 실패 (+ 검증 시 errors)
{ "success": false, "code": "USER-002", "message": "이미 사용 중인 이메일입니다." }
```

## 테스트
```bash
./gradlew test
```
통합 테스트는 기본적으로 **Testcontainers(PostgreSQL)** 를 사용한다(실 환경 일치).

주요 커버리지:
- **재고 동시성**(`OrderConcurrencyIntegrationTest`): 재고 5에 동시 주문 20건 → 정확히 5건 성공, 오버셀링 0건
- **멀티셀러 주문 분리**: 여러 판매자 상품이 SubOrder로 분리되고 SubOrder 단위 부분 취소/환불
- **결제 멱등성** + 쿠폰/포인트 적용·복원, **포인트 lot FIFO 만료**
- **셀러 정산**: 판매자별 집계·수수료 차감, 재정산 방지, 취소건 제외, 수수료율 정책 변경
- **권한 격리**: 본인 주문/장바구니/정산만 접근, 판매자 상품 소유권 검증
- **실 PG(토스) 어댑터**: confirm 호출/Basic 인증/금액 위변조 거절을 `MockRestServiceServer` 로 검증
- **상품 검색/인기 상품**: 키워드·카테고리·판매자 동적 검색, 판매량 집계 기반 인기 상품 정렬

## 결제 게이트웨이 전환
`PaymentGateway` 인터페이스로 추상화되어 있고 `@ConditionalOnProperty` 로 구현체를 고른다.
- 기본(`payment.gateway=mock`): 외부 통신 없이 승인하는 `MockPaymentGateway`
- 실 PG(`payment.gateway=toss`): `TossPaymentGateway` 가 `/v1/payments/confirm` 으로 승인 확정.
  클라이언트 결제위젯이 발급한 `paymentKey` 를 `POST /api/payments/{orderId}` 본문으로 받아 전달하며,
  승인 응답의 `totalAmount` 가 서버 계산 금액과 다르면 위변조로 보고 거절한다.
```yaml
payment:
  gateway: toss
  toss:
    base-url: https://api.tosspayments.com
    secret-key: ${TOSS_SECRET_KEY}
```

### 환경 주의
1. **Gradle 런처 JDK**: 시스템 JDK가 26이면 Gradle 8.14.x의 내장 Kotlin 컴파일러가 깨진다.
   `~/.gradle/gradle.properties` 에 `org.gradle.java.home=<JDK 21 경로>` 를 둔다.
2. **Testcontainers ↔ Docker**: Testcontainers 1.21.4 로 Docker Engine 29 까지 호환된다(`build.gradle.kts`).
   컨테이너를 띄울 수 없는 환경이면 외부 PostgreSQL 로 우회한다(CI 도 이 경로 — `IT_DATASOURCE_*`):
   ```bash
   docker run -d --name starter-it-pg -e POSTGRES_DB=starter -e POSTGRES_USER=starter \
     -e POSTGRES_PASSWORD=starter -p 55433:5432 postgres:16-alpine
   ./gradlew test -Dit.datasource.url=jdbc:postgresql://localhost:55433/starter
   ```

## 관리자 API — 스타터 킷(사용자/감사로그)
> `ROLE_ADMIN` 전용. URL 규칙 + `@PreAuthorize` 이중 방어. (이커머스 관리자 API는 위 "이커머스 API" 참조)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/admin/users?keyword=&status=&page=&size=` | 사용자 동적 검색(Kotlin JDSL) |
| GET | `/api/admin/users/{id}` | 사용자 상세 |
| PATCH | `/api/admin/users/{id}/status` | 상태 변경(ACTIVE/LOCKED/…) |
| POST | `/api/admin/users/{id}/roles` | 역할 부여 |
| DELETE | `/api/admin/users/{id}/roles/{role}` | 역할 회수 |
| GET | `/api/admin/audit-logs?userId=&method=&statusCode=&uriKeyword=&from=&to=` | 감사 로그 동적 검색 |

> 검색은 조건이 null 이면 자동으로 where 절에서 빠지는 **Kotlin JDSL 동적 쿼리**로 구현되어 있다.

## 감사 로그
모든 요청을 `AuditLogFilter`(Security 체인 내, 인가 이후)에서 캡처해 **비동기(`@Async`)** 로 저장한다.
- 기록: method, uri, ip, user-agent, 상태코드, 소요시간(ms), 사용자, **payload(JSONB)**
- payload 의 민감 키(`password`, `token` 등)는 자동 **마스킹**(`app.audit.mask-keys`)
- 제외 경로/본문 길이 등은 `app.audit.*` 로 설정
- PostgreSQL `jsonb` + GIN 인덱스로 유연한 검색:
  ```sql
  SELECT * FROM audit_logs WHERE payload @> '{"body":{"email":"x@example.com"}}';
  ```

## 설계 메모
- **DB는 PostgreSQL 단일화**(H2 제외): 확장 시 JSONB/파티셔닝 등 PostgreSQL 고유 기능과
  테스트 환경을 일치시키기 위함. 빠른 테스트는 DB 없는 단위 테스트(MockK)로 충족한다.
- **RBAC**: `User ─ Role ─ Permission` 구조. 권한 문자열은 역할명(`ROLE_*`)과 권한명을 모두 포함해
  `@PreAuthorize("hasRole(...)")` / `hasAuthority(...)` 양쪽을 지원한다.
