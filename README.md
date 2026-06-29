# E-Commerce Platform (B2C 마켓플레이스 백엔드)

Kotlin + Spring Boot **스타터 킷 위에 구축한 B2C 마켓플레이스 이커머스 백엔드**.
다수 판매자가 입점하고, 고객(또는 게스트)이 상품을 탐색·주문·결제하며, 쿠폰/포인트·배송·환불·셀러 정산까지 지원한다.
스타터 킷의 인증/RBAC/감사로그/공통응답을 그대로 재사용하고 그 위에 이커머스 도메인을 확장했다.

> 제품 요구사항·설계 결정의 전체 맥락은 [`PRD.md`](PRD.md) 참조.

## 이커머스 핵심 개념
- **마켓플레이스(멀티 셀러)**: 한 주문에 여러 판매자 상품이 섞이면 **SubOrder(판매자 단위)** 로 분리된다. 결제는 Order 단위 1건, 배송·취소·정산은 SubOrder 단위.
- **재고 정합성**: 초과 판매 0건이 최우선. **원자적 UPDATE**(`UPDATE ... WHERE quantity - reserved >= qty`)로 동시 주문에도 오버셀링을 막는다.
- **금액은 전부 서버 계산**: 상품합계 − 쿠폰할인 − 포인트사용 = 결제금액. 클라이언트 금액은 신뢰하지 않는다.
- **게스트 주문**: 비회원도 주문/결제 가능(주문번호+연락처로 조회). 장바구니·쿠폰·포인트는 회원 전용.
- **관리자 설정값**: 포인트 적립률/유효기간, 정산 수수료율은 코드 상수가 아니라 DB 정책 행으로 런타임 변경한다.

---

## (기반) Spring Starter Kit
이 프로젝트의 토대인 스타터 킷은 세션 로그인 / OAuth2 소셜 로그인 / RBAC 권한 / 공통 응답·에러 / 감사 로그 / 관리자 기능을 제공한다.

## 기술 스택
- **언어/빌드**: Kotlin, Gradle(Kotlin DSL), JVM 21 toolchain
- **프레임워크**: Spring Boot 3.5.x (Web MVC, Security, Data JPA, OAuth2 Client)
- **쿼리**: [Kotlin JDSL](https://github.com/line/kotlin-jdsl) — 타입 안전한 동적 쿼리
- **DB**: PostgreSQL (로컬/운영), Flyway 마이그레이션
- **테스트**: JUnit5, MockK, SpringMockK, Testcontainers, spring-security-test

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

> 기반 스타터 킷 단계(공통 응답/에러, RBAC, OAuth2, 감사로그, 관리자)는 모두 완료된 상태에서 출발한다.

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
    ├── catalog/   카테고리·상품·옵션·재고(원자적 차감), 상품 검색
    ├── cart/      회원 장바구니 + 게스트 무상태 계산 API
    ├── order/     Order·SubOrder·OrderItem, 생성/취소/부분취소, 게스트 주문
    ├── payment/   Payment·PaymentEvent, PaymentGateway(Mock/Toss), 멱등 결제
    ├── coupon/    Coupon·IssuedCoupon, 관리자 발행
    ├── point/     PointAccount·PointLot(FIFO)·PointTransaction, 적립/사용/만료, 정책
    ├── member/    내 쿠폰/포인트 조회(MeController)
    └── settlement/ 셀러 정산(판매자별 집계·수수료 차감), 수수료 정책
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
| GET | `/api/auth/me` | 내 정보 | 세션 |

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

## 이커머스 API
> 모든 응답은 공통 규약 `{ success, code, message, data }`. 상태 변경 요청은 CSRF 토큰 필요.

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
| POST | `/api/orders/{id}/cancel` | 주문 전체 취소 | 회원 |
| POST | `/api/orders/sub-orders/{id}/cancel` | SubOrder 부분 취소/환불 | 회원 |
| POST | `/api/payments/{orderId}` | 결제 요청(멱등) | 회원/게스트 |
| GET | `/api/me/coupons` · `/api/me/points` | 내 쿠폰/포인트 | 회원 |

### 판매자 API (`ROLE_SELLER`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/api/seller/apply` | 입점 신청(일반 회원 허용) |
| POST · PUT | `/api/seller/products` · `/api/seller/products/{id}` | 상품 등록/수정 |
| PATCH | `/api/seller/products/{id}/stock` | 재고 조정(절대값) |
| GET | `/api/seller/orders?status=` | 본인 판매분 SubOrder 조회 |
| POST | `/api/seller/orders/{subOrderId}/ship` | 송장 등록 → SHIPPED |
| GET | `/api/seller/settlements` | 본인 상점 정산 내역 |

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
통합 테스트는 기본적으로 **Testcontainers(PostgreSQL)** 를 사용한다(실 환경 일치). 총 **75개** 통합/단위 테스트.

주요 커버리지:
- **재고 동시성**(`OrderConcurrencyIntegrationTest`): 재고 5에 동시 주문 20건 → 정확히 5건 성공, 오버셀링 0건
- **멀티셀러 주문 분리**: 여러 판매자 상품이 SubOrder로 분리되고 SubOrder 단위 부분 취소/환불
- **결제 멱등성** + 쿠폰/포인트 적용·복원, **포인트 lot FIFO 만료**
- **셀러 정산**: 판매자별 집계·수수료 차감, 재정산 방지, 취소건 제외, 수수료율 정책 변경
- **권한 격리**: 본인 주문/장바구니/정산만 접근, 판매자 상품 소유권 검증

### 환경 주의 (이 개발 머신 한정)
1. **Gradle 런처 JDK**: 시스템 JDK가 26이면 Gradle 8.14.x의 내장 Kotlin 컴파일러가 깨진다.
   `~/.gradle/gradle.properties` 에 `org.gradle.java.home=<JDK 21 경로>` 를 둔다.
2. **Testcontainers ↔ Docker**: 일부 Docker Desktop 환경에서 docker-java 가 데몬에 접속하지
   못한다. 이 경우 외부 PostgreSQL 을 직접 띄우고 우회 실행:
   ```bash
   docker run -d --name pg -e POSTGRES_DB=starter -e POSTGRES_USER=starter \
     -e POSTGRES_PASSWORD=starter -p 5433:5432 postgres:16-alpine
   ./gradlew test -Dit.datasource.url=jdbc:postgresql://localhost:5433/starter
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
