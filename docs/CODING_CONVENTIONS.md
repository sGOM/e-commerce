# 코딩 컨벤션

이 저장소 코드가 **이미 따르고 있는 규칙**을 정리한 문서다. 새 코드는 주변 코드와 같은 모양이어야 한다.

- **기계적 규칙**(들여쓰기·줄바꿈·import 순서·따옴표)은 도구가 정하고 CI 가 막는다. 사람이 외우지 않는다 → [1. 포맷](#1-포맷--도구가-정한다)
- **판단 규칙**(레이어·트랜잭션·예외·테스트 모양)은 이 문서에 둔다. 각 규칙의 `예:` 파일이 기준 구현이다.
- 규칙과 다르게 써야 할 이유가 생기면 코드보다 **이 문서를 먼저 고친다**(같은 PR 에서).
- 도메인 불변식(금액 서버 계산·원자적 재고·Flyway)은 [`CLAUDE.md`](../CLAUDE.md), 커밋/PR 규칙은 [`GIT_CONVENTIONS.md`](GIT_CONVENTIONS.md), 비CRUD 설계 이유는 [`SERVER_ARCHITECTURE.md`](SERVER_ARCHITECTURE.md).

---

## 1. 포맷 — 도구가 정한다

| 대상 | 규칙 원천 | 고치기 | 검사(CI) |
|---|---|---|---|
| Kotlin (`src/**`, `*.gradle.kts`) | 루트 `.editorconfig`(ktlint `intellij_idea` + 저장소 스타일과 충돌하는 규칙 off) | `./gradlew ktlintFormat` | `./gradlew ktlintCheck` |
| TS/TSX/CSS (`frontend/src`) | `frontend/.prettierrc.json`(세미콜론 없음, 작은따옴표, 120자) | `cd frontend && npm run format` | `npm run format:check` |
| TS 린트 | `frontend/.oxlintrc.json` | — | `npm run lint` |

- `frontend/src/components/ui/**` 는 shadcn 생성물이라 prettier 대상이 아니다(원본 유지). 직접 고치지 말고 재생성한다.
- 대량 포맷 커밋은 `.git-blame-ignore-revs` 에 등록한다(로컬: `git config blame.ignoreRevsFile .git-blame-ignore-revs`).
- 규칙을 바꾸려면 `.editorconfig`/`.prettierrc.json` 을 고치고 전체 포맷을 **별도 `style:` 커밋**으로 낸다.

## 2. 공통

- **주석은 한국어, "왜"를 쓴다.** 무엇을 하는지는 이름이 말하게 하고, 비자명한 결정(동시성·금액·외부 API 제약)은 KDoc/JSDoc 에 이유와 공식 문서 링크를 남긴다.
  예: `payment/PaymentService.kt` 의 `refund`, `order/UnpaidOrderExpiryService.kt`
- 식별자는 영어, 사용자에게 보이는 메시지는 한국어.
- 기획 문서 수용기준을 구현한 코드는 주석에 `(AC7)`, `docs/planning/<slug>.md §4` 처럼 출처를 단다. 예: `order/OrderService.kt` 의 `cancelSubOrder`
- 의도적 단순화는 한계와 확장 경로를 주석으로 남긴다(예: "현재 규모에서 매 요청 집계 — 트래픽 증가 시 캐싱").

## 3. 백엔드 (Kotlin / Spring)

### 3.1 패키지·파일
- `domain/<도메인>/` 아래 `XxxController`, `XxxService`, `dto/`, `entity/`, `repository/` (+ 필요 시 `gateway/`). 예: `domain/coupon/`
- 역할별 API 는 같은 도메인 패키지에 접두어로 나눈다: `AdminXxxController`/`AdminXxxService`, `SellerXxx…`. 예: `order/AdminOrderController.kt`
- 도메인 공통이 아닌 코드만 `common/`(응답·예외·설정)·`security/` 에 둔다.

### 3.2 컨트롤러
- 얇게: 요청 → 서비스 호출 → `ApiResponse.success(data, "한국어 메시지")`. 로직·트랜잭션을 두지 않는다. 예: `coupon/AdminCouponController.kt`
- 로그인 사용자는 `@AuthenticationPrincipal principal: CustomUserDetails` 로 받고 `principal.userId` 를 서비스에 넘긴다.
- 요청 본문은 `@RequestBody @Valid request: XxxRequest`.
- 권한은 `SecurityConfig` URL 규칙이 1차(`/api/admin/**`, `/api/seller/**`), 공개 경로 추가 시 반드시 거기에 `permitAll` 을 명시한다.

### 3.3 서비스·트랜잭션
- 클래스에 `@Transactional(readOnly = true)`, 상태를 바꾸는 메서드에만 `@Transactional`. 예: `payment/PaymentService.kt`
- 금액·상태 전이 계산은 서비스/엔티티에서. 클라이언트 값을 믿지 않는다.
- **외부 호출(PG 등)은 내부 변경을 끝낸 뒤 마지막에**, 실패는 예외로 전체 롤백. 재시도 안전성은 결정적 멱등키로. (SERVER_ARCHITECTURE §6-4)
- 동시에 같은 행을 바꾸는 경로는 원자적 조건부 UPDATE 또는 `@Lock(PESSIMISTIC_WRITE)` 로 중재한다. 애플리케이션 락(`synchronized`) 금지.
- 같은 클래스 안의 자기 호출에는 `@Transactional` 이 적용되지 않는다. 건별 격리가 필요하면 **별도 빈 + `REQUIRES_NEW`**. 예: `gift/GiftExpiryProcessor.kt`

### 3.4 엔티티
- 생성자 프로퍼티 + `@Column(name = "snake_case", nullable = …, length = …)`, id 는 본문의 `val id: Long? = null`(IDENTITY). 예: `coupon/entity/Coupon.kt`
- 상태 변경은 **의미 있는 도메인 메서드**로(`markPaid`, `expire`, `cancel`), 외부에서 직접 못 바꿀 필드는 `protected set`. 예: `gift/entity/GiftClaim.kt`
- enum 은 `@Enumerated(EnumType.STRING)`. 스키마는 Flyway 새 파일로만 바꾼다.

### 3.5 DTO
- 도메인별 `dto/XxxDtos.kt` 한 파일에 요청·응답을 모은다(ktlint `filename` 규칙을 끈 이유).
- 이름: 요청 `CreateXxxRequest`/`UpdateXxxRequest`, 응답 `XxxResponse`.
- 응답은 `companion object { fun from(entity) }` 로 엔티티에서 만든다. 엔티티를 그대로 반환하지 않는다. 예: `coupon/dto/CouponDtos.kt`
- 검증은 `@field:NotBlank`, `@field:NotNull`, `@field:Size` … (Kotlin 에서는 `@field:` 대상 지정 필수). 중첩 객체는 `@field:Valid`.

### 3.6 예외
- 사용자·클라이언트가 유발할 수 있는 실패는 **`BusinessException(ErrorCode.X)`**. 새 코드는 `ErrorCode` 에 `DOMAIN-NNN` 으로 추가하고 HTTP 상태를 고른다. 예: `common/exception/ErrorCode.kt`
- 존재를 숨겨야 하는 리소스(남의 주문)는 403 대신 **404** 로 응답한다. 예: `order/OrderService.kt` 의 `cancelSubOrder`
- `require`/`check`/`IllegalStateException` 은 **프로그래머 오류용 불변식**에만. 전역 핸들러에서 500 이 되므로 사용자 입력 검증에 쓰지 않는다.
- `requireNotNull(entity.id)` 는 영속 엔티티 id 언랩 관례로 허용.

### 3.7 조회
- 단순 조회는 Spring Data 파생 메서드, **조건이 선택적인 검색은 Kotlin JDSL**(`KotlinJdslJpqlExecutor`), 집계·성능 쿼리만 `nativeQuery`. 예: `admin/AdminUserService.kt`(JDSL), `order/repository/OrderRepository.kt`(native 집계)
- N+1 이 보이면 `@EntityGraph` 로 필요한 연관만. 예: `order/repository/OrderItemRepository.kt`

### 3.8 설정·외부 연동·배치
- 묶음 설정은 `@ConfigurationProperties` data class(`XxxProperties`), 단일 값은 `@Value("\${key:기본값}")`. 예: `payment/gateway/PaymentProperties.kt`
- 운영 중 바뀌는 비즈니스 값(적립률·수수료율)은 설정이 아니라 **DB 정책 행**.
- 외부 연동은 인터페이스 + 설정으로 고르는 구현 2개(실제/Mock), 키가 없어도 앱이 뜨고 테스트가 돈다. 예: `payment/gateway/`, `notification/email/`
- 스케줄러는 `@ConditionalOnProperty(prefix = "<x>.scheduler", name = ["enabled"], havingValue = "true")` 로 **기본 비활성** + 관리자 수동 실행 엔드포인트 + `application.yml` 주석 예시. `@Scheduled` 의 `${}` 에는 기본값을 반드시 쓴다. 예: `cart/CartReminderScheduler.kt`
- 로깅: `private val log = LoggerFactory.getLogger(javaClass)`, 배치는 건별 실패를 `log.error(..., ex)` 후 계속.

### 3.9 백엔드 테스트
- 통합 테스트는 `AbstractIntegrationTest` 상속 + `@AutoConfigureMockMvc` + MockMvc Kotlin DSL, 기본은 클래스에 `@Transactional`(롤백). 예: `payment/PaymentIntegrationTest.kt`
- 외부 게이트웨이는 `@MockkBean`, HTTP 어댑터는 `MockRestServiceServer`. 예: `payment/gateway/TossPaymentGatewayTest.kt`
- 테스트 이름은 **백틱 한국어 문장**으로 기대 동작을 쓴다: ``fun `결제 전 주문 취소는 PG 를 호출하지 않는다`()``.
- 데이터는 테스트 안에서 직접 시드(`seedXxx` private 헬퍼), 고유 이메일/SKU 로 충돌을 피한다.
- 롤백을 검증하려고 `@Transactional` 을 뺀 테스트는 커밋된 데이터를 정리한다(CLAUDE.md "작업 흐름" 참고). 예: `payment/PaymentCancelIntegrationTest.kt`

## 4. 프론트엔드 (React / TS)

### 4.1 구조·import
- `src/api`(client·endpoints·types), `src/pages`(라우트 화면, `admin/`·`seller/` 하위), `src/components`(재사용 UI), `src/hooks`, `src/lib`(순수 로직), `src/labels.ts`(enum → 한국어 라벨).
- 앱 모듈은 **상대 경로**(`../api/endpoints`), shadcn 과 유틸만 **별칭**(`@/components/ui/button`, `@/lib/utils`).
- 타입 전용 import 는 `import type`.
- 페이지·컴포넌트는 `export default function XxxPage()`. 파일당 컴포넌트 하나가 기본(작은 하위 컴포넌트는 같은 파일 허용).

### 4.2 API 호출
- 서버 호출은 `endpoints.ts` 의 타입드 함수로만(`orderApi.pay(...)`). 컴포넌트에서 `fetch` 직접 호출 금지 — `client.ts` 가 CSRF 헤더와 응답 봉투를 처리한다.
- 새 API 는 `types.ts` 에 백엔드 DTO 와 같은 이름·필드로 타입을 추가한다.
- 에러 표시: `err instanceof ApiError ? err.message : '한국어 기본 메시지'`. 예: `pages/CheckoutPage.tsx`
- 결과 알림은 `sonner` 의 `toast`, 화면 내 오류는 `role="alert"` 문단.

### 4.3 상태·스타일
- 서버 상태는 `useState` + `useEffect` 로 불러온다(전역 상태 라이브러리 없음). 여러 화면이 공유하면 `hooks/` 의 컨텍스트 훅. 예: `hooks/useWishlist.tsx`
- 스타일은 Tailwind 유틸리티 + shadcn 컴포넌트, 조건부 클래스는 `cn()`. 색은 디자인 토큰(`text-muted-foreground`, `bg-success/10`)을 쓰고 임의 색값을 새로 만들지 않는다. [`storefront-ui-spec.md`](design/storefront-ui-spec.md)
- enum 을 화면에 보일 때는 `labels.ts` 매핑을 거친다.
- 폼 입력에는 `aria-label` 또는 `<Label>` 을 붙인다(테스트도 이것으로 찾는다).

### 4.4 프론트 테스트
- 파일 첫 줄 `// @vitest-environment happy-dom`, `vi.mock('../api/endpoints', …)` 로 API 를 대체하고 Testing Library 로 사용자 관점(역할·라벨·텍스트)에서 검증한다. 예: `pages/CheckoutPage.test.tsx`
- `it('…')` 이름은 한국어 문장.
- 순수 로직(`lib/`)은 같은 폴더의 `*.test.ts`. 예: `lib/payment.test.ts`

## 5. PR 전 자가 점검

- [ ] `./gradlew ktlintFormat` / `npm run format` 실행 후 테스트 통과
- [ ] 새 오류는 `ErrorCode` + `BusinessException`, 새 공개 경로는 `SecurityConfig` 에 명시
- [ ] 변경 메서드에 `@Transactional`, 외부 호출은 마지막
- [ ] 응답은 DTO `from()`, 엔티티 직접 반환 없음
- [ ] 프론트 서버 호출은 `endpoints.ts` 경유
- [ ] 비자명한 결정에 "왜" 주석(+ 커밋 본문)
- [ ] 규칙과 다르게 썼다면 이 문서도 수정
