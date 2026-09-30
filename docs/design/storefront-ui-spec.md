# 스토어프론트 UI 규칙 (shadcn/ui + Tailwind CSS v4)

> 대상: `frontend/` 전 화면(고객·판매자·관리자). shadcn/ui 도입과 라이트/다크 토큰화는 완료됐다.
> 이 문서는 **새 화면을 만들 때 지킬 규칙**만 둔다. 값의 원천은 코드다:
>
> | 무엇 | 원천 |
> |---|---|
> | 색·반경 토큰(라이트/다크, OKLCH) | `frontend/src/index.css` |
> | shadcn 컴포넌트 | `frontend/src/components/ui/` — 생성물이라 직접 고치지 않고 재생성([CODING_CONVENTIONS §1](../CODING_CONVENTIONS.md#1-포맷--도구가-정한다)) |
> | enum → 한국어 라벨·배지 색·알림 아이콘 | `frontend/src/labels.ts` |

---

## 1. 도입 때 해결한 문제 (D코드)

기획서들이 이 코드로 인용한다. 새 화면에서 같은 문제를 다시 만들지 않는다.

| # | 문제 | 지금의 규칙 |
|---|---|---|
| D1 | 원색 유틸리티(`indigo-600` 등) 하드코딩 | 토큰 유틸리티만(`bg-primary`, `text-muted-foreground`) |
| D2 | 다크모드 불가 | 모든 색은 CSS 변수 토큰 경유 |
| D3 | radius·간격·타이포 혼재 | §3.2·§3.3 스케일 |
| D4 | 로딩이 "불러오는 중…" 텍스트 | 레이아웃과 같은 모양의 `skeleton` |
| D5 | 피드백이 인라인 `<p>`, 라이브 영역 없음 | `sonner` 토스트(`role=status`) |
| D6 | placeholder 만 있는 입력 | `<Label htmlFor>` + `id` |
| D7 | 28~32px 터치 타깃 | 44×44px 이상 |
| D8 | 품절·준비중이 같은 회색 배지 | 부록 A 상태별 배지 |
| D9 | 페이지 버튼 전부 나열 | `pagination`(첫/현재±1/마지막 + `…`) |
| D10 | 포커스 링 없음 | `focus-visible:ring` (shadcn 기본) |
| D11 | 수량 `−`/`+` 에 이름 없음 | 아이콘 버튼은 전부 `aria-label` |
| D12 | 헤더 nav 과밀(모바일 넘침) | md 미만은 `sheet` 드로어, 계정 메뉴는 `dropdown-menu` |

## 2. 디자인 방향

1. **신뢰 우선** — 가격·재고·상태를 명확히, CTA 위치 예측 가능, 스켈레톤으로 레이아웃 흔들림(CLS) 제거.
2. **상품이 주인공** — 크롬은 뉴트럴, 브랜드색(Indigo 계열 `primary`)은 CTA·가격·핵심 상태에만(표면의 10% 이하).
3. **모바일 우선** — 기본 1열, 브레이크포인트에서 확장(`sm` 640 / `md` 768 / `lg` 1024).
4. **라이트/다크 동등** — 두 테마 모두 WCAG AA.
5. 톤: 얇은 보더 + 낮은 그림자. 과한 그림자·그라디언트 금지.

## 3. 토큰

### 3.1 대비 원칙
값은 `index.css` 가 원천. 토큰을 바꾸면 아래를 라이트/다크 모두 재확인한다.
- `foreground`/`background` ≥ 4.5:1, `muted-foreground`/`background` ≥ 4.5:1
- `primary-foreground`/`primary`, `destructive-foreground`/`destructive` ≥ 4.5:1
- `ring`/`background` ≥ 3:1
- **가격·매장명 등에 `muted-foreground` 보다 옅은 색 금지**(예전 `slate-400` 은 흰 배경 3:1 미만이었다).

### 3.2 타이포 스케일
| 토큰 | Tailwind | 용도 |
|---|---|---|
| H1 | `text-2xl font-bold` | 페이지 제목, 상세 상품명 |
| H2 | `text-lg font-semibold` | 섹션 제목 |
| Body | `text-sm` | 카드·목록 본문 |
| Body-lg | `text-base` | 폼 입력값, 상세 설명 |
| Caption | `text-xs` | 매장명·메타·배지 (12px 미만 금지) |
| Price | `font-bold text-primary` | 가격은 목록·상세·장바구니 모두 이 규칙 |

### 3.3 간격 · 반경 · 그림자
- 페이지 패딩 `px-4`/`md:px-6`, 섹션 간 `mb-8`, 카드 내부 `p-4`, 폼 필드 간 `space-y-4`.
- 폭: 목록 `max-w-7xl`, 폼·읽기 영역 `max-w-md`~`max-w-2xl`.
- Radius: 카드·버튼 `rounded-lg`, 배지·칩 `rounded-full`, 큰 이미지 `rounded-xl`. 임의 혼용 금지.
- 그림자: 카드 `shadow-sm`, 호버 `shadow-md`, 오버레이(dialog/sheet)만 `shadow-lg`.

## 4. 컴포넌트

`components/ui/` 에 있는 것을 먼저 쓴다(button, card, input, label, badge, select, skeleton, separator,
sonner, sheet, dropdown-menu, pagination, textarea). 새 shadcn 컴포넌트는 `npx shadcn@latest add` 로 추가한다.

## 5. 화면 규칙

**공통 상태 규약(모든 데이터 화면)**
- 로딩: 실제 레이아웃과 같은 모양·개수의 `skeleton`.
- 에러: 페이지 단위는 중앙 카드(메시지 + "다시 시도"), 액션 단위는 `sonner` 토스트(destructive).
- 빈 상태: 설명 + 다음 행동 CTA.
- 성공 액션: 토스트(짧게). 이동이 있으면 토스트 + 이동.

### 5.1 공통 레이아웃 (`Layout.tsx`)
- sticky 헤더. md 이상 인라인 nav, 미만은 `sheet` 트리거(`aria-label="메뉴 열기"`).
- 장바구니 배지: 개수 0이면 숨김, **로딩 중에도 숨김**(깜빡임 방지), `aria-label="장바구니, N개"`.
- 테마 토글: 초기값 `prefers-color-scheme`, 선택은 localStorage, 첫 페인트 전 `.dark` 선반영(FOUC 방지).
- nav 활성 `text-primary font-semibold`, 비활성 `text-muted-foreground`.

### 5.2 상품 목록·카드
- 그리드 2 → `sm:3` → `lg:4` 열. 카테고리 칩은 가로 스크롤.
- 카드 전체가 링크, 접근성 이름 = 상품명 + 가격. 상태 배지는 부록 A(ON_SALE 은 미표시).
- 빈 결과에 검색·필터가 있으면 "필터 초기화" 버튼.

### 5.3 상품 상세
- 1열 → `md:2열`. 품절 옵션은 disabled + "(품절)" 접미, 수량 max = 선택 옵션 재고.
- 구매 불가면 CTA disabled + 이유 문구. 담기 결과는 토스트("장바구니 보기" 액션).

### 5.4 장바구니
- 재고 부족 항목은 `text-destructive` "재고 부족(가용 N)", 하나라도 있으면 "주문하기" disabled.
- 수량 변경 중에는 해당 stepper disabled(중복 요청 방지).
- 게스트면 "비회원" 배지 + "로그인 시 장바구니가 병합됩니다." 안내.

### 5.5 로그인·회원가입
- 모든 입력에 `Label` 연결, `autoComplete`(email / current-password / new-password / name).
- 실패 시 **비밀번호 필드를 비우지 않는다**(재입력 부담). 필드 오류는 `aria-invalid` + `aria-describedby`.

### 5.6 찜·등급·알림함
- 찜 하트(`WishlistButton`): `aria-label` "찜하기"/"찜 해제", 비로그인 클릭은 토스트로 로그인 유도,
  카드 링크 클릭과 이벤트 분리(`stopPropagation`), 히트박스 44px.
- 찜 목록: 가격 인하 배지 + 원가 취소선(`line-through text-muted-foreground`) + 현재가.
- 내 등급: 진행바로 다음 등급까지 남은 금액. 혜택은 "등급 전용 쿠폰"(포인트 배수 아님)으로 담백하게.
- 알림함: 제목·본문·이동은 서버값(`title`/`body`/`linkUrl`) 그대로, 프론트는 타입별 아이콘만 분기(`labels.ts`).
  안읽음은 `bg-accent/40`.

## 6. 접근성 체크리스트 (WCAG 2.1 AA)

### 6.1 색·대비
- [ ] §3.1 대비 기준을 라이트/다크 모두 충족.
- [ ] 상태를 색으로만 전달하지 않는다 — 배지에 텍스트 병행.

### 6.2 포커스 / 키보드
- [ ] 모든 인터랙티브 요소에 `focus-visible` 링.
- [ ] 탭 순서 논리적(헤더 → 필터 → 목록 → 페이지네이션).
- [ ] Sheet/Dialog: 포커스 트랩, ESC 닫힘, 닫으면 트리거로 복귀(shadcn 기본).
- [ ] 커스텀 stepper 키보드 조작 가능.

### 6.3 시맨틱 / ARIA
- [ ] 페이지당 `<h1>` 하나, 섹션은 `<h2>`.
- [ ] 랜드마크 `header`/`nav`/`main`/`footer`.
- [ ] 아이콘 전용 버튼 전부 `aria-label`(장바구니, 삭제, 수량 ±, 메뉴, 테마).
- [ ] 페이지네이션 현재 페이지 `aria-current="page"`.
- [ ] 카테고리 칩 그룹에 역할(tablist/group)과 선택 상태.

### 6.4 폼
- [ ] 연결된 `<label>`, placeholder 는 라벨 대용 금지.
- [ ] 에러는 `aria-describedby` 로 연결 + `aria-invalid`, 필수 필드는 `required` + 시각 표시.

### 6.5 터치 / 모바일
- [ ] 탭 가능한 컨트롤 ≥ 44×44px, 인접 타깃 간격 ≥ 8px.
- [ ] 카테고리 칩 외 본문 가로 스크롤 없음.
- [ ] `prefers-reduced-motion` 존중.

---

## 부록 A. 상태 배지 매핑

실제 클래스는 `labels.ts` 가 원천이고, 이 표는 의미(variant) 기준이다.

| 대상 | 값 | Badge variant | 라벨 |
|---|---|---|---|
| 상품 | ON_SALE | (미표시) | 판매중 |
| 상품 | SOLD_OUT | destructive | 품절 |
| 상품 | DRAFT / HIDDEN | secondary | 준비중 / 숨김 |
| 인기 | soldQuantity | secondary | N개 판매 |
| 장바구니 | !purchasable | destructive(텍스트) | 재고 부족 |
| 계정 | 비회원 | outline | 비회원 |
| 찜 | isPriceDropped | destructive | ▼ N% 인하 |
| 로열티 | BRONZE / SILVER / GOLD / VIP | 뉴트럴 → 강조 순 | 브론즈 / 실버 / 골드 / VIP |
| 주문 | CREATED / PAID / SHIPPED·DELIVERED / CANCELED | warning / success / default·success / destructive | — |
