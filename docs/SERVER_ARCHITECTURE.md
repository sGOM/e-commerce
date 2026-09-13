# 서버 아키텍처 설명서

서버 개발자를 위한 내부 구조 문서. CRUD API는 컨트롤러를 보면 되므로 여기서는 **단순 API에서 벗어난 부분** —
동시성·금액 정합성·결제/정산·인증 파이프라인·동적 쿼리·감사 로그 — 을 *어떤 기술로 어떻게 구현했는지* 중심으로 정리한다.

> 패키지: `com.example.starter`. 화폐는 `Long`(KRW), 비율은 basis point(100 = 1%). 스키마는 Flyway 전담.

---

## 0. 한눈에 보는 설계 원칙

| 원칙 | 구현 수단 |
|------|-----------|
| 클라이언트 금액을 신뢰하지 않는다 | 모든 금액은 `Service`에서 서버 계산 (`Order.recalculateAmounts()`) |
| 초과 판매 0건 | 재고 예약을 **단일 원자적 UPDATE**로 처리 (DB가 동시성 중재) |
| 중복 결제 0건 | `order_id` UNIQUE + 멱등 분기 |
| 운영값은 배포 없이 변경 | 적립률/유효기간/수수료율을 **DB 정책 행**으로 관리 |
| 기능 토글은 설정으로 | `@ConditionalOnProperty` 로 빈 등록 자체를 켜고 끔 |
| 읽기/쓰기 경계 명확화 | 서비스 클래스 기본 `@Transactional(readOnly = true)`, 변경 메서드만 `@Transactional` |

---

## 1. 요청 처리 파이프라인 (Security FilterChain)

`SecurityConfig` 가 세션 기반 인증 + SPA 친화 CSRF + 감사 로그를 한 체인에 엮는다. 필터 **순서**가 핵심이다.

```
요청 ──► CsrfFilter ──► CsrfCookieFilter ──► ... ──► AuthorizationFilter ──► AuditLogFilter ──► Controller
                         (쿠키 강제 발급)                  (인가)            (인가 이후 = 사용자 확정)
```

### 1-1. CSRF — 쿠키 토큰 방식 (`XSRF-TOKEN` → `X-XSRF-TOKEN`)
세션 기반이라 CSRF가 필요하지만 SPA라 폼 hidden 필드를 못 쓴다. 그래서 `CookieCsrfTokenRepository.withHttpOnlyFalse()`
로 토큰을 **JS가 읽을 수 있는 쿠키**에 싣는다. 문제는 Spring Security 6의 CSRF 토큰이 **지연(deferred) 로딩**이라,
누군가 토큰을 "사용"하기 전까지 쿠키가 응답에 실리지 않는다는 점. 이를 `CsrfCookieFilter`가 매 요청에서 강제 렌더링한다.

```kotlin
// CsrfCookieFilter — CsrfFilter 바로 뒤에 등록
val csrfToken = request.getAttribute(CsrfToken::class.java.name) as? CsrfToken
csrfToken?.token   // 값을 읽는 행위 자체가 쿠키 기록을 트리거
filterChain.doFilter(request, response)
```
클라이언트(fetch 래퍼)는 GET 응답으로 받은 `XSRF-TOKEN` 쿠키를 읽어 상태 변경 요청에 `X-XSRF-TOKEN` 헤더로 되돌려준다.

### 1-2. 인가 — URL 규칙 + `@PreAuthorize` 이중 방어
1차로 `authorizeHttpRequests`의 URL 매칭(`/api/admin/** → hasRole("ADMIN")`, 공개 경로 `permitAll`),
2차로 컨트롤러/서비스의 `@PreAuthorize("hasRole(...)")`. 한쪽 설정 실수가 곧바로 권한 누수로 이어지지 않게 한다.
세션은 `HttpSessionSecurityContextRepository`에 저장하고, 동시 세션은 1개로 제한한다.

> **알려진 제약**: 권한이 로그인 시점 세션에 고정되므로, 판매자 승인 직후 권한 반영에는 재로그인이 필요하다(ROADMAP 3.4).

---

## 2. 동시성 & 재고 정합성 — DB가 중재하는 원자적 예약

오버셀링 방지가 최우선 요구사항. 애플리케이션 락(`synchronized`)이나 비관적 락 없이, **조건부 단일 UPDATE**로
DB 행 수준 락에 동시성 중재를 위임한다. `available = quantity - reserved` 모델.

```kotlin
// InventoryRepository.reserve — 영향 행 수가 0이면 재고 부족
@Modifying(flushAutomatically = true)
@Query("""
    UPDATE Inventory i SET i.reserved = i.reserved + :qty
     WHERE i.option.id = :optionId
       AND i.quantity - i.reserved >= :qty
""")
fun reserve(optionId: Long, qty: Int): Int
```

`OrderService.buildOrder`에서 라인별로 `reserve()`를 호출하고 **반환값이 0이면 즉시 예외**를 던진다.
예외는 주문 트랜잭션 전체를 롤백시키므로, 같은 주문에서 앞서 잡아둔 예약도 함께 원복된다.

```kotlin
if (inventoryRepository.reserve(line.optionId, line.quantity) == 0) {
    throw BusinessException(ErrorCode.INSUFFICIENT_STOCK, "'${line.productName}' 의 재고가 부족합니다.")
}
```

- **왜 벌크 UPDATE인가**: 읽고-검사하고-쓰는(read-check-write) 사이의 race를 없앤다. `WHERE` 조건이 곧 검사이고,
  DB가 한 번에 원자적으로 처리한다. 테스트(`OrderConcurrencyIntegrationTest`)에서 재고 5에 동시 주문 20건 → 정확히 5건만 성공.
- **flush 주의**: JPQL 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 호출 전 변경을 flush(`flushAutomatically = true`)한다.
  단, 1차 캐시를 `clear`하지는 않는다 — 같은 트랜잭션에서 cart/seller 등 다른 영속 엔티티가 detach되는 부작용이 더 크기 때문.
- 취소/결제 실패 시 `release()`가 `reserved`를 되돌린다(`reserved >= qty`일 때만 → 음수 방지).

---

## 3. 멀티셀러 주문 모델 — Order / SubOrder 분리

마켓플레이스라 한 주문에 여러 판매자 상품이 섞인다. **결제는 Order 1건, 배송·취소·정산은 SubOrder(판매자) 단위**라는
요구를 엔티티 구조로 표현한다.

```
Order (결제·쿠폰·포인트·배송지)
  └─< SubOrder (판매자 1명, 배송/취소/정산 단위, payableShare)
        └─< OrderItem (주문 시점 가격·상품명 스냅샷)
```

`OrderService.buildOrder`가 라인을 `groupBy { seller.id }`로 묶어 SubOrder를 만든다. 가격/상품명/옵션명은
**주문 시점 스냅샷**으로 OrderItem에 박제해, 이후 원본 상품이 바뀌어도 주문 내역은 불변이다.

```kotlin
lines.groupBy { it.seller.id }.values.forEach { sellerLines ->
    val subOrder = SubOrder(seller = sellerLines.first().seller)
    sellerLines.forEach { line -> subOrder.addItem(OrderItem(/* 스냅샷 */)) }
    subOrder.recalculateSubtotal()
    order.addSubOrder(subOrder)
}
```

### payable 배분 — 부분 환불의 기반
쿠폰/포인트는 Order 전체에 적용되지만 환불은 SubOrder 단위로 일어난다. 그래서 최종 결제금액을
SubOrder별 `payableShare`로 **배분**(`order.distributePayable()`)해 둔다. SubOrder 하나를 부분 취소하면
그 `payableShare`만큼만 부분 환불하고, **모든 SubOrder가 취소된 시점**에 쿠폰/포인트 복원·적립 회수·결제 취소를 마무리한다.

```kotlin
// cancelSubOrder: 부분 환불 → 전부 취소되면 마무리
if (wasPaid) paymentRepository.findByOrderId(orderId).ifPresent { it.recordPartialRefund(subOrder.payableShare) }
if (order.isFullyCanceled) { /* 쿠폰/포인트 복원 + 적립 회수 + 결제 markCanceled */ }
```

---

## 4. 금액 계산 파이프라인 — 전부 서버 계산

클라이언트가 보낸 금액은 절대 신뢰하지 않는다. 순서가 정해져 있다: **상품합계 → 쿠폰 할인 → 포인트 사용 → 재계산**.

```kotlin
// OrderService.createFromCart
request.issuedCouponId?.let { order.discountAmount = couponService.applyToOrder(userId, it, orderId, order.totalAmount) }
if (request.usePoint > 0) {
    val maxUsable = order.totalAmount - order.discountAmount   // 포인트는 쿠폰 적용 후 잔액 한도
    pointService.use(userId, request.usePoint, orderId, maxUsable)
    order.pointUsed = request.usePoint
}
order.recalculateAmounts()   // payableAmount = 합계 - 할인 - 포인트
order.distributePayable()    // SubOrder별 배분
```

결제 시에도 게이트웨이에 넘기는 금액은 `order.payableAmount`(서버 계산본)이고, 클라이언트가 보낸 값이 아니다.

---

## 5. 포인트 — lot 원장(FIFO) 기반

단일 잔액 컬럼이 아니라 **적립 단위(lot) 원장**으로 관리한다. 각 lot이 만료일을 가지므로 FIFO 소멸과 부분 만료가 가능하다.
적립률/유효기간은 코드 상수가 아니라 `PointPolicy`(관리자 설정 DB 행)를 따른다.

```kotlin
// PointService
fun use(...)       // 주문 시 차감. 결제금액 초과/잔액 부족이면 예외 → 주문 롤백
fun earn(...)      // 결제 확정 시 정책 적립률 적용 + 만료일(expiresAtFrom) 부여
fun restoreUse(...)// 취소/실패 시 새 lot으로 환원(만료일 재부여)
fun revokeEarnForOrder(...) // 결제 취소 시 그 주문으로 적립된 lot 잔여 회수
fun expireDuePoints()       // 만료일 지난 lot 소멸 (스케줄러/관리자 트리거)
```

- **적립 타이밍**: 주문 생성이 아니라 **결제 확정(`PaymentService.pay`) 직후** `earn()`. 미결제 주문에 포인트가 붙지 않는다.
- **회수 vs 환원 구분**: 내가 *쓴* 포인트는 `restoreUse`로 돌려받고, 내가 결제로 *적립받은* 포인트는 취소 시 `revokeEarn`으로 회수한다.
- 도메인 규칙은 엔티티(`PointAccount.use/earn/cancelUse/expireDue`)에 두고, 서비스는 정책 조회와 트랜잭션 경계만 담당.

---

## 6. 결제 — 게이트웨이 추상화 + 멱등성 + 위변조 검증

### 6-1. PaymentGateway 추상화 (`@ConditionalOnProperty`로 구현체 선택)
서비스는 `PaymentGateway` 인터페이스에만 의존한다. 설정값으로 빈 등록 자체를 바꿔 Mock ↔ 실 PG를 전환한다.

```kotlin
@ConditionalOnProperty(prefix = "payment", name = ["gateway"], havingValue = "mock", matchIfMissing = true)
class MockPaymentGateway       // 외부 통신 없이 승인

@ConditionalOnProperty(prefix = "payment", name = ["gateway"], havingValue = "toss")
class TossPaymentGateway       // /v1/payments/confirm 로 승인 확정
```

### 6-2. 멱등성 — 중복 결제 차단
`payments.order_id`에 UNIQUE 제약을 두고, 서비스에서 **이미 PAID면 재승인 없이 기존 결제를 반환**한다.
재시도/더블클릭에도 한 주문은 한 번만 결제된다.

```kotlin
val existing = paymentRepository.findByOrderId(orderId).orElse(null)
if (existing != null && existing.status == PaymentStatus.PAID) return PaymentResponse.from(existing)
if (order.status != OrderStatus.CREATED) throw BusinessException(ErrorCode.ORDER_NOT_PAYABLE)
```

### 6-3. Toss 어댑터 — Basic 인증 + 금액 위변조 거절
실 PG는 클라이언트 결제위젯이 발급한 `paymentKey`를 서버가 받아 confirm으로 확정하는 흐름.
시크릿 키는 `{secretKey}:`(콜론 뒤 빈 비밀번호)를 base64로 인코딩한 Basic 인증으로 보낸다.
**승인 응답의 `totalAmount`가 서버 계산 금액과 다르면 위변조로 보고 거절**(결제 본체 롤백)한다.

```kotlin
when {
    response?.status != "DONE" -> PaymentApproveResult(false, ...)
    response.totalAmount != null && response.totalAmount != command.amount ->
        PaymentApproveResult(false, null, "승인 금액 불일치")   // 위변조 거절
    else -> PaymentApproveResult(true, response.paymentKey, "승인")
}
```
- 테스트는 `MockRestServiceServer`로 confirm 호출/Basic 인증/금액 위변조 거절을 외부 통신 없이 검증한다
  (`RestClient.Builder`를 생성자로 주입받는 이유).

---

## 7. 정산 — 집계 멱등성 + 조건부 스케줄러

### 7-1. 멱등한 집계
`SettlementService.generate()`는 **`settlement_id IS NULL`인 SubOrder(취소 제외 상태)만** 대상으로 판매자별 집계 후,
처리한 SubOrder에 `settlementId`를 찍는다. 다음 실행은 이미 찍힌 건을 자연히 건너뛴다 → 같은 주문은 두 번 정산되지 않는다.

```kotlin
val targets = subOrderRepository.findBySettlementIdIsNullAndStatusIn(SETTLEABLE)
targets.groupBy { it.seller.id }.map { (_, subOrders) ->
    val sales = subOrders.sumOf { it.subtotal }
    val commission = sales * rateBp / 10_000          // 수수료율은 정책 DB 행(basis point)
    settlementRepository.save(Settlement(payoutAmount = sales - commission, ...))
    subOrders.forEach { it.settlementId = settlement.id }   // 재정산 방지 마킹
}
```

### 7-2. 주기 자동화 (`@Scheduled` + `@ConditionalOnProperty`)
스케줄러 빈은 `settlement.scheduler.enabled=true`일 때만 등록된다. 수동 트리거(관리자 API)와 **동일 로직**을 호출하고,
집계가 멱등하므로 중복 실행도 안전하다.

```kotlin
@ConditionalOnProperty(prefix = "settlement.scheduler", name = ["enabled"], havingValue = "true")
class SettlementScheduler(private val settlementService: SettlementService) {
    @Scheduled(cron = "\${settlement.scheduler.cron:0 0 4 * * MON}", zone = "\${settlement.scheduler.zone:Asia/Seoul}")
    fun run() { settlementService.generate() }
}
```
> **구현 함정**: `@Scheduled`의 `cron`/`zone`은 `@ConfigurationProperties` 기본값을 읽지 못한다(Environment 프로퍼티가 아니라서).
> 그래서 placeholder에 **기본값(`:0 0 4 * * MON`)을 직접** 넣어야 빈 등록 시 해석 에러가 안 난다. `@EnableScheduling`은 `SchedulingConfig`에서 활성화.

---

## 8. 조회 — Kotlin JDSL 동적 쿼리 + 네이티브 집계

### 8-1. 동적 검색 (`KotlinJdslJpqlExecutor`)
조건이 `null`이면 `where` 절에서 자동으로 빠지는 타입 안전한 동적 쿼리. 문자열 SQL 조립이나 `if`-분기 없이 검색 조건을 합성한다.

```kotlin
productRepository.findPage(pageable) {
    select(entity(Product::class)).from(entity(Product::class))
        .whereAnd(
            path(Product::status).`in`(ProductStatus.VISIBLE),
            condition.keyword?.let { path(Product::name).like("%$it%") },        // null이면 무시
            condition.categoryId?.let { path(Product::category).path(Category::id).eq(it) },
            condition.sellerId?.let { path(Product::seller).path(Seller::id).eq(it) },
        )
        .orderBy(path(Product::id).desc())
}
```
같은 패턴을 관리자 사용자 검색/주문 검색/감사 로그 검색에도 쓴다. N+1은 상세 조회에서 `@EntityGraph`로 옵션·재고를 함께 로딩해 막는다.

### 8-2. 인기 상품 — 네이티브 집계 후 도메인 필터
"많이 팔린 순"은 JPA 그래프 탐색으로 표현하기 번거롭고 무겁다. 그래서 **판매 수량 집계는 네이티브 SQL**로 한 방에 뽑고,
노출 상태 필터링만 애플리케이션에서 적용한다(관심사 분리).

```sql
SELECT po.product_id, SUM(oi.quantity)
FROM order_items oi
JOIN product_options po ON po.id = oi.option_id
JOIN sub_orders so ON so.id = oi.sub_order_id
WHERE so.status IN ('PAID','PREPARING','SHIPPED','DELIVERED')   -- 취소 제외
GROUP BY po.product_id ORDER BY SUM(oi.quantity) DESC LIMIT :limit
```
```kotlin
// ProductService.getPopular — 랭킹 순서 보존이 포인트
val soldById = rows.associate { (it[0] as Number).toLong() to (it[1] as Number).toLong() }
val productById = productRepository.findAllById(soldById.keys).associateBy { it.id }
return soldById.keys.mapNotNull { id ->            // soldById(LinkedHashMap)의 정렬 순서 = 판매 랭킹
    productById[id]?.takeIf { it.status.isVisible }?.let { PopularProductResponse.from(it, soldById[id]!!) }
}
```
- `findAllById`는 순서를 보장하지 않으므로, **집계가 매긴 순서(`soldById.keys`)를 기준으로** 다시 매핑해 랭킹을 유지한다.
- 품절(`OUT_OF_STOCK`)은 노출, 숨김/삭제(`DRAFT/HIDDEN`)는 제외 — `ProductStatus.isVisible`로 표현.
- 현재 매 요청 집계라 트래픽이 늘면 캐싱이 필요(ROADMAP 7.3).

---

## 9. 감사 로그 — 보안 체인 내 필터 + 비동기 JSONB

모든 요청을 `AuditLogFilter`가 캡처해 비동기로 저장한다. 위치와 비동기화가 설계 포인트.

- **위치**: Security 체인의 `AuthorizationFilter` **이후**에 등록 → `SecurityContextHolder`에서 인증된 사용자를 안전하게 읽는다.
- **본문 캡처**: 서블릿 입력 스트림은 한 번만 읽히므로 `ContentCachingRequestWrapper/ResponseWrapper`로 감싸 캐시하고,
  `finally`에서 `copyBodyToResponse()`로 실제 응답에 본문을 복사한다(빼먹으면 응답 바디가 사라짐).
- **마스킹**: payload를 JSON 파싱 후 민감 키(`password`, `token` …)를 **재귀적으로** `***`로 치환(`app.audit.mask-keys`).
- **비동기 저장**: `auditLogService.saveAsync(...)`(`@Async`, `AsyncConfig`)로 요청 지연에 영향 없음.
  로그 적재 실패가 본 요청을 깨뜨리지 않게 `runCatching`으로 감싼다.
- **저장/검색**: PostgreSQL `jsonb` + GIN 인덱스. `payload @> '{"body":{"email":"x@..."}}'` 같은 유연한 컨테인먼트 검색.

```kotlin
try {
    filterChain.doFilter(wrappedReq, wrappedRes)
} finally {
    runCatching { auditLogService.saveAsync(buildEntry(wrappedReq, wrappedRes, durationMs)) }
        .onFailure { log.warn("감사 로그 기록 실패: {}", it.message) }
    wrappedRes.copyBodyToResponse()
}
```

---

## 10. 트랜잭션 · 예외 · 응답 규약

- **트랜잭션 전략**: 서비스 클래스에 `@Transactional(readOnly = true)`를 기본으로 걸고, **변경 메서드만** `@Transactional` 오버라이드.
  읽기 경로에서 의도치 않은 flush/dirty checking 비용을 없애고, 쓰기 경계를 코드로 명시한다.
- **예외 → HTTP 매핑**: 도메인 실패는 `BusinessException(ErrorCode)`로 던지고 `GlobalExceptionHandler`가 한곳에서 HTTP 상태·코드로 변환.
  `ErrorCode`는 `{도메인}-{번호}`(예: `ORDER-007`) 체계로 상태/메시지를 한 enum에 모은다.
- **공통 응답**: 모든 응답은 `ApiResponse<T>` = `{ success, code, message, data }`. 페이지는 `PageResponse<T>`(`content` 필드).
- **존재 은닉**: 남의 주문/SubOrder 접근은 `403`이 아니라 `NOT_FOUND`로 응답해 리소스 존재 자체를 숨긴다(`cancelSubOrder` 참고).

---

## 11. 설정 기반 토글 한눈에

| 설정 키 | 효과 | 구현 |
|---------|------|------|
| `payment.gateway` = `mock`/`toss` | 결제 게이트웨이 구현체 선택 | `@ConditionalOnProperty` |
| `settlement.scheduler.enabled` | 주기 자동 정산 스케줄러 등록 여부 | `@ConditionalOnProperty` |
| `settlement.scheduler.cron`/`zone` | 정산 주기/타임존 | `@Scheduled` placeholder |
| `membership.billing.scheduler.enabled` | 멤버십/정기배송 정기결제 청구 스케줄러 | `@ConditionalOnProperty` |
| `loyalty.scheduler.enabled` | 로열티 등급 주기 재계산 스케줄러(기본 off, 수동 트리거) | `@ConditionalOnProperty` |
| `cart-reminder.scheduler.enabled` | 장바구니 이탈 리마인드 발송 스케줄러(기본 off) | `@ConditionalOnProperty` |
| `loyalty.silverThreshold`/`goldThreshold`/`vipThreshold` | 등급 임계 순구매액(원) | `@ConfigurationProperties(loyalty)` |
| `loyalty-coupon.couponIdByTier` | 승급 시 발급할 등급별 쿠폰 매핑(미등록 시 미발급) | `@ConfigurationProperties(loyalty-coupon)` |
| `cart-reminder.inactivityHours` | 이탈 판정 미활동 시간(기본 24h) | `@ConfigurationProperties(cart-reminder)` |
| OAuth2 client registration 유무 | 소셜 로그인 활성화 | 런타임 `ClientRegistrationRepository` 존재 검사 |
| `app.audit.*` | 감사 로그 on/off·제외경로·마스킹 키·본문 길이 | `@ConfigurationProperties` |
| 적립률·유효기간·수수료율·리뷰적립 | 런타임 변경(배포 불필요) | **DB 정책 행** (`PointPolicy`, `SettlementPolicy`, `ReviewPolicy`) |

---

## 12. 스키마 관리 (Flyway)

스키마는 Hibernate가 아니라 **Flyway 버전 스크립트**(`src/main/resources/db/migration/V*.sql`)가 단독 관리한다.
Hibernate는 `ddl-auto: validate`로 **매핑↔스키마 일치 검증만** 한다. 통합 테스트도 Testcontainers PostgreSQL에서
같은 마이그레이션을 돌려 실 환경과 일치시킨다(H2 미사용 — JSONB/GIN 등 PostgreSQL 고유 기능 때문).

```
V1 init · V2 oauth · V3 audit_logs · V4 catalog · V5 cart · V6 order · V7 payment
V8 coupon_point · V9 order_shipping_address · V10 shipment · V11 sub_order payable_share
V12 point_expiry · V13 settlement · V14 sub_order_delivery · V15 reviews
V16 restock_alert_and_notification · V17 collections · V18 flash_sales · V19 delivery_slots
V20 memberships · V21 delivery_subscriptions · V22 gift_orders
V23 wishlist · V24 loyalty_tier · V25 cart_reminder
```

---

## 13. 이벤트 기반 알림 & 멱등 배치 (커머스 스위트·리텐션)

Phase 9 이후 확장된 기능들은 세 가지 공통 패턴 위에 얹혀 있다. 새 기능을 추가할 때 이 패턴을 재사용한다.

### 13-1. 범용 인앱 알림함 (`notification`)
재입고·가격인하·카트리마인드·멤버십·정기배송 등 서로 다른 사건이 **하나의 알림함**으로 모인다.
`Notification`은 `type`(enum) + `title`/`body`/`linkUrl`만 가진 범용 엔티티라, 새 알림 종류는
**enum 값 추가만으로** 스키마 변경 없이 확장된다(`PRICE_DROP`, `CART_REMINDER`가 그 예). 프론트
알림함은 `linkUrl`로 이동하고 `type`으로 아이콘만 분기하므로, 백엔드가 값만 채우면 화면 변경이 없다.

### 13-2. 트랜잭션 분리 이벤트 발송 (`@Async` + `AFTER_COMMIT`)
"본 작업 트랜잭션을 지연시키지 않는다"는 원칙 아래, 부수 효과(알림)는 이벤트로 분리한다. 재입고
알림(`InventoryRestockedEvent`)과 가격 인하 알림(`ProductPriceChangedEvent`)이 같은 골격이다.

```kotlin
// 발행: 도메인 서비스가 상태 변경 커밋 경로에서 이벤트만 publish
if (product.basePrice > newPrice) publisher.publishEvent(ProductPriceChangedEvent(productId, newPrice))

// 수신: 커밋 이후 비동기로 알림 생성 → 원 트랜잭션(상품 수정)을 붙잡지 않음
@TransactionalEventListener(phase = AFTER_COMMIT)
@Async("notificationExecutor")
fun on(e: ProductPriceChangedEvent) { wishlistService.notifyPriceDrop(e.productId, e.newPrice) }
```
- **왜 AFTER_COMMIT**: 롤백된 변경에 알림이 나가는 것을 막는다. 롤백 기반 통합 테스트에서는 커밋이
  없어 리스너가 안 뜨므로, 서비스 메서드(`notifyPriceDrop`)를 직접 호출해 핵심 로직을 검증한다.
- **재알림 정책**: 재입고 알림은 1회 소멸성이지만, 가격 인하는 발송 시 `baselinePrice`를 현재가로
  **갱신**해 연속 인하를 계속 추적한다(반복 이벤트).

### 13-3. 멱등 배치 (스케줄러 조건부 등록 + 실패 격리)
정산과 동일하게, 주기 작업은 `@ConditionalOnProperty`로 스케줄러 빈을 켜고(기본 off, 관리자 수동
트리거 병행) **멱등**하게 설계한다. 로열티 등급·카트 리마인드가 그 예다.

- **로열티 등급**(`LoyaltyTierBatchService`): 최근 12개월 순구매액(취소/환불 제외)을 네이티브 집계해
  등급을 재계산(강등 포함). 승급이 감지되면 `LoyaltyTierBenefitService`가 **별도 트랜잭션**으로 등급
  전용 쿠폰을 발급하는데, `existsByCouponIdAndUserId`로 **이미 받은 쿠폰이면 건너뛰어**(멱등) 배치가
  여러 번 돌아도 중복 발급되지 않는다. 쿠폰 발급 실패는 try/catch로 격리해 등급 갱신을 되돌리지 않는다.
- **카트 리마인드**(`CartReminderBatchService`): `lastActivityAt`이 임계 시간(기본 24h)보다 오래된
  **비어있지 않은** 장바구니를 스캔해 알림 1회 발송. `lastReminderAt < lastActivityAt` 조건으로
  **중복 발송을 dedup**하고, 건별 실패를 격리한다. 자동 쿠폰 발급은 어뷰징 방지를 위해 하지 않는다.

> 화폐/정책/동시성 원칙은 앞 절들과 동일하다. 이 세 패턴만 익히면 리텐션 계열 기능은 기존 도메인에
> 최소 침습으로 얹을 수 있다.

---

## 부록: 도메인 패키지 빠른 지도

| 패키지 | 책임 | 눈여겨볼 클래스 |
|--------|------|-----------------|
| `catalog` | 상품·옵션·재고·카테고리, 검색/인기 | `InventoryRepository`(원자적 UPDATE), `ProductService`(JDSL/인기) |
| `order` | Order/SubOrder, 생성·취소·환불 | `OrderService`(멀티셀러 분리·취소 캐스케이드) |
| `payment` | 결제, 게이트웨이 | `PaymentService`(멱등), `TossPaymentGateway`(위변조) |
| `point` | 포인트 lot 원장 | `PointService`(적립/사용/환원/만료) |
| `coupon` | 쿠폰 발행/적용/복원 | `CouponService` |
| `settlement` | 셀러 정산·스케줄러 | `SettlementService`(멱등 집계), `SettlementScheduler` |
| `seller` | 입점/심사, 송장 | `SellerOrderService` |
| `review` | 상품 리뷰/포토리뷰, 구매 인증·요약 | `ReviewService`(구매내역 검증), `ReviewPolicy` |
| `restock` | 재입고 알림(옵션 임계값 감지) | `RestockAlertEventListener`(@Async·AFTER_COMMIT) |
| `notification` | 범용 인앱 알림함 | `NotificationType`(enum), `NotificationService`(@Async 발송) |
| `promotion` | 기획전/컬렉션 큐레이션 | `CollectionService` |
| `flashsale` | 플래시세일/타임딜 | `FlashSaleService`(재고 원자성 재사용) |
| `delivery` | 배송 권역·슬롯 예약 | `DeliverySlotService`(슬롯 정원 동시성) |
| `membership`·`billing` | 유료 멤버십 + 정기결제 빌링 | `MembershipService`, `BillingScheduler`(빌링키 청구) |
| `subscription` | 정기배송 구독 | `SubscriptionService`(반복 주문 생성) |
| `gift` | 선물하기 + 기프트 클레임 | `GiftService`(토큰 수령·배송지 없는 결제) |
| `wishlist` | 위시리스트 + 가격 인하 알림 | `WishlistPriceAlertEventListener`(§13, restock 패턴 재사용) |
| `loyalty` | 로열티 등급 + 승급 쿠폰 | `LoyaltyTierBatchService`, `LoyaltyTierBenefitService`(§13, 멱등 발급) |
| `cart` | 장바구니 + 이탈 리마인드 | `CartReminderBatchService`(§13, dedup 발송) |
| `common/audit` | 감사 로그 | `AuditLogFilter`(필터 위치·마스킹) |
| `common/config` | 보안·스케줄링·비동기·JDSL 설정 | `SecurityConfig`(필터 순서) |
