# 유료 멤버십 (컬리멤버스 유사 구독)

> 우선순위: **P2** (Could — 정기결제 인프라 선행 필요)
> 관련 도메인: `payment`(정기결제 확장), `point`(적립률 우대), `order`(무료배송/혜택 적용), 신규 `membership`(가칭) 도메인

## 1. 개요 및 배경

마켓컬리는 "컬리멤버스"라는 유료 구독 멤버십으로 무료배송, 적립 우대, 회원 전용 혜택을 제공해
록인 효과를 만든다. 우리 플랫폼은 쿠폰/포인트는 있지만 "매월 결제하는 구독"이라는 개념 자체가
없고, 무엇보다 **정기결제(빌링키 기반 자동 결제) 인프라가 전무**하다(현재 `PaymentGateway`는
1회성 주문 결제만 처리). 장기적으로 록인 효과가 크지만, 신규 인프라 투자가 선행되어야 해 P2로
분류한다.

## 2. 목표 및 성공지표

- 목표: 월 구독료를 내는 회원에게 무료배송·포인트 적립 우대·전용 쿠폰 등의 혜택을 제공해 재구매
  주기를 단축한다.
- 성공지표(제안)
  - 멤버십 가입 전환율, 갱신율(리텐션), 해지율
  - 멤버십 회원의 평균 구매 빈도/객단가 vs 비회원 비교
  - 정기결제 실패율

## 3. 유저 스토리 & 수용기준(AC)

### US-1. 회원은 멤버십을 구독한다
- AC1: 결제수단(카드) 등록 후 구독을 시작하면 즉시 혜택이 활성화되고, `nextBillingAt`(다음 결제일,
  기본 1개월 후)이 설정된다.
- AC2: 최초 가입 시 프로모션(예: 첫 달 무료)은 이번 범위에서는 다루지 않는다(§8).

### US-2. 회원은 언제든 해지할 수 있다
- AC3: 해지 시 즉시 혜택이 사라지지 않고, **이미 결제한 기간(`nextBillingAt` 이전)까지는 혜택
  유지** 후 자동 만료된다(선불 구독의 일반적 관례).
- AC4: 해지 후에도 이력은 남고, 재가입 시 새로운 구독 주기가 시작된다.

### US-3. 정기결제가 매월 자동으로 이루어진다
- AC5: `nextBillingAt` 도달 시 배치가 등록된 빌링키로 결제를 시도한다.
- AC6: 결제 실패 시 최대 N회(정책값) 재시도하고, 모두 실패하면 구독을 `PAST_DUE` → 유예기간 경과
  후 `EXPIRED`로 전이하며 혜택을 중단한다.
- AC7: 결제 성공 시 `nextBillingAt`을 다음 주기로 갱신한다.

### US-4. 멤버십 회원은 주문 시 혜택을 자동 적용받는다
- AC8: 무료배송(배송비 모델이 있다는 전제 하에 배송비 0원 처리).
- AC9: 포인트 적립률이 우대 배율(예: 1.5배)로 적용된다 — 기존 `PointPolicy.earnRateBp`에 회원의
  멤버십 배율을 곱해 계산.
- AC10: 멤버십 전용 쿠폰(신규 발급 대상 조건에 "멤버십 활성" 추가) 수령 가능.

## 4. 정책/규칙 & 예외

- **혜택 판정 기준**: 주문/결제 시점에 `Membership.status = ACTIVE`인지로 판정(주문 후 해지되어도
  이미 적용된 혜택은 유지).
- **중복 결제 방지**: 정기결제 배치도 기존 결제 멱등성 원칙(주문ID 기반)과 유사하게, "이번 주기
  청구 ID" 단위로 멱등 처리해야 한다(같은 주기 이중 청구 방지).
- **유예기간**: 결제 실패 시 즉시 해지 대신 유예기간(정책값, 예: 3일)을 두어 카드 재발급 등 일시적
  실패를 흡수.
- **게스트 미지원**: 회원 전용(쿠폰/포인트와 동일 원칙).

## 5. 데이터 모델 / API 개략 (spring-expert 참고)

```
Membership
  id, userId(FK, unique-ish — 활성 구독은 1개), plan(BASIC/PREMIUM), status(ACTIVE/PAST_DUE/CANCELED/EXPIRED)
  price, startAt, nextBillingAt, canceledAt(nullable)

MembershipBillingKey
  id, userId(FK), gatewayBillingKey(PG 발급 토큰), cardLast4, registeredAt

MembershipBillingHistory
  id, membershipId(FK), attemptedAt, status(SUCCESS/FAILED), failureReason, paymentId(nullable FK)

PointPolicy 확장 또는 MembershipBenefitPolicy(신규)
  membershipEarnMultiplierBp (예: 15000 = 1.5배)
```

- **정기결제 게이트웨이 확장**: 기존 `PaymentGateway` 인터페이스는 1회성 승인(confirm) 전용이라,
  빌링키 발급/자동결제 실행을 위한 별도 인터페이스(`RecurringPaymentGateway` 가칭)가 필요하다.
  토스페이먼츠는 빌링 전용 API가 별도로 있어(카드 등록 → 빌링키 발급 → 주기적 자동승인), 기존
  `TossPaymentGateway`와는 다른 어댑터로 분리하는 것을 제안(spring-expert 확정).
- **배치**: 기존 `SettlementScheduler`(`@ConditionalOnProperty` + `@Scheduled`) 패턴 재사용해
  `MembershipBillingScheduler` 신설.
- 신규 마이그레이션: `memberships`, `membership_billing_keys`, `membership_billing_histories`.

### API 개략
| 메서드 | 경로 | 설명 | 인증 |
|---|---|---|---|
| POST | `/api/me/membership/billing-key` | 카드 등록(빌링키 발급) | 회원 |
| POST | `/api/me/membership` | 구독 시작 | 회원 |
| DELETE | `/api/me/membership` | 해지(예약) | 회원 |
| GET | `/api/me/membership` | 내 구독 상태/혜택 요약 | 회원 |
| GET | `/api/admin/memberships?status=` | 관리자 구독 현황 검색 | 관리자 |

## 6. 프론트 화면/플로우 개략 (react-expert · uiux-expert 참고)

- **마이페이지 내 멤버십 카드**: 미가입 시 혜택 소개 + 가입 CTA, 가입 시 다음 결제일/혜택 요약 +
  해지 버튼.
- **가입 플로우**: 카드 등록(PG 위젯) → 약관 동의 → 가입 완료. 기존 체크아웃의 PG 위젯 연동 패턴을
  준용.
- **체크아웃 화면**: 멤버십 회원에게는 "무료배송 적용됨", "포인트 1.5배 적립 예정" 같은 인라인
  안내 배지 노출.
- **관리자 백오피스**: 구독 현황 테이블(상태별 카운트), 결제 실패 이력 조회.

## 7. 기존 기능과의 연계/영향

- **payment 도메인**: 신규 정기결제 어댑터 추가는 기존 1회성 결제 흐름과 독립적으로 설계해
  기존 `PaymentService`/`PaymentGateway` 계약을 깨지 않는다.
- **point 도메인**: 적립 계산 시 `PointPolicy.earnRateBp`에 멤버십 배율을 곱하는 지점만 추가,
  기존 lot 기반 FIFO 구조는 변경 불필요.
- **order 도메인**: 배송비 개념이 생긴다면 주문 금액 계산 파이프라인(`recalculateAmounts`)에
  배송비 항목과 멤버십 무료배송 예외 처리가 추가되어야 한다(배송비 모델 자체가 선행 과제).

## 8. 범위 (In / Out of scope)

**In scope**
- 구독 가입/해지, 정기결제 자동화, 결제 실패 재시도/유예, 포인트 적립 우대 배율

**Out of scope (이번 단계 제외)**
- 첫 달 무료 등 가입 프로모션
- 플랜 다양화(BASIC/PREMIUM 등 여러 티어의 세부 혜택 차등) — 데이터 모델만 plan 필드로 여지 확보
- 무료배송 실제 로직(배송비 모델 확정 후)
- 멤버십 전용 상품/특가

## 9. 오픈 이슈 (결정 필요)

1. **정기결제 PG 확정**: 토스페이먼츠 빌링 API를 쓸지, 별도 PG를 검토할지 — 계약/정산 조건 확인
   필요.
2. **배송비 모델 부재**: 무료배송 혜택을 구체화하려면 배송비 자체가 먼저 설계되어야 한다.
3. **가격/플랜 정책**: 월 구독료, 단일 플랜 vs 다중 플랜 여부.
4. **정산 영향**: 멤버십 혜택(포인트 우대)으로 인한 추가 비용을 플랫폼이 부담하는 것으로 가정 —
   재무 검토 필요.
