# 스토어프론트 UI 리디자인 스펙 (shadcn/ui + Tailwind CSS v4)

> 대상: 고객 스토어프론트(`frontend/`) · React 19 + Vite + TS + Tailwind v4
> 목적: 커머스에 맞는 신뢰감 있는 시각 언어를 확립하고, shadcn/ui 도입 기준으로 라이트/다크 모두 지원하는 재사용 컴포넌트 체계를 정의한다.
> 이 문서는 **명세**다. 실제 구현은 react-expert가 담당한다. 각 항목은 "무엇을·어디에·어떤 상태로" 수준까지 지정한다.
>
> 문서 순서: (1) 진단 → (2) 디자인 방향 → (3) 토큰 → (4) 컴포넌트 → (5) 화면별 스펙 → (6) 접근성

---

## 1. 현재 UI 진단

읽은 파일: `Layout.tsx`, `ProductCard.tsx`, `ProductListPage.tsx`, `ProductDetailPage.tsx`, `CartPage.tsx`, `LoginPage.tsx`, `SignupPage.tsx`, `index.css`.

### 1.1 시스템/토큰 레벨
| # | 문제 | 근거 | 영향 |
|---|---|---|---|
| D1 | **테마 토큰 부재.** `indigo-600`, `slate-400/600/900`, `red-500`, `green-600` 등 원색 유틸리티가 컴포넌트마다 하드코딩. | 전 파일에서 반복. | 브랜드 컬러 일괄 변경 불가, 다크모드 불가, 상태색 불일치. |
| D2 | **다크모드 미지원.** `index.css`가 `color-scheme: light` 고정, body 배경 `#f8fafc` 하드코딩. | `index.css` L5, L10. | 야간/OS 다크 선호 사용자 경험 저하. 태스크 요구사항 미충족. |
| D3 | **간격/타이포 스케일 비일관.** 카드 radius가 `rounded-xl`(카드)·`rounded-2xl`(상세 이미지)·`rounded-lg`(input) 혼재, 텍스트 크기 규칙 없음. | ProductCard vs ProductDetail. | 화면 간 리듬 깨짐. |

### 1.2 컴포넌트/상호작용 레벨
| # | 문제 | 근거 | 영향 |
|---|---|---|---|
| D4 | **로딩이 텍스트 한 줄.** "불러오는 중…"만 표시, 스켈레톤 없음. | ProductList L122, Detail L59, Cart L76. | 레이아웃 시프트(CLS), 체감 로딩 느림. |
| D5 | **피드백이 인라인 텍스트.** 담기 성공/에러가 `<p>`로만. `role="status"`/`aria-live` 없음. | Detail L118-119, Cart L83. | 스크린리더가 변화 통지 못 함, 시선 밖에서 발생 시 놓침. |
| D6 | **폼 라벨 미연결.** 로그인/회원가입 input이 `placeholder`만 사용, `<label htmlFor>` 없음. | Login L34-49, Signup L34-56. | 스크린리더 라벨 상실, placeholder 사라지면 맥락 소실(WCAG 3.3.2 위반). |
| D7 | **터치 타깃 미달.** 장바구니 수량 `h-7 w-7`(28px), 페이지네이션 `h-8 w-8`(32px). | Cart L118/125, List L151. | 최소 44×44 CSS px 미달(WCAG 2.5.5/2.5.8). 모바일 오조작. |
| D8 | **상태 배지 단조.** 품절/준비중/숨김을 모두 `bg-slate-200` 회색 하나로 표기. | ProductCard L34, Detail L76. | 품절과 준비중 구분 약함, 위험/정보 위계 없음. |
| D9 | **페이지네이션 무한 나열.** 전체 페이지를 버튼으로 모두 렌더. | List L146-158. | 페이지 20+일 때 가로 붕괴, 현재 위치 파악 난이도. |
| D10 | **포커스 가시성 부재.** 커스텀 버튼/링크에 `focus-visible` 링 스타일 없음. | 전 버튼. | 키보드 사용자 현재 위치 상실(WCAG 2.4.7). |
| D11 | **셀렉트/수량 접근성.** 옵션 `<select>`는 OK지만 수량 `+/−` 버튼에 `aria-label` 없음(문자 "−"만). | Cart, Detail. | 스크린리더 "마이너스" 오독. |
| D12 | **모바일 네비 과밀.** 헤더 nav가 상품/장바구니/주문/쿠폰/판매자/관리자까지 한 줄 `flex gap-4`. | Layout L23-65. | 좁은 화면에서 줄바꿈/넘침, 장바구니 수량 배지 없음. |

### 1.3 정보구조 레벨
- **장바구니 진입 지표 없음** — 헤더 "장바구니"에 담긴 개수 배지가 없어 회귀 동선 약함.
- **상품 카드에 카테고리/평점/할인 표현 여지 없음** — `categoryName`이 타입에 있으나 미노출.
- **CTA 위계 단일** — 모든 주요 버튼이 동일한 indigo 채움. 보조 액션(계속 쇼핑 등)과 구분 안 됨.

---

## 2. 디자인 방향 / 톤

### 2.1 원칙
1. **신뢰(Trust) 우선.** 커머스의 전환은 신뢰에서 나온다 — 명확한 가격·재고·상태 표기, 예측 가능한 CTA 위치, 흔들리지 않는 레이아웃(스켈레톤으로 CLS 제거).
2. **상품이 주인공.** UI 크롬은 뉴트럴(무채색 근처)로 물러서고, 채도 있는 브랜드색은 CTA·가격·핵심 상태에만 절제 사용(약 10% 이하 표면).
3. **모바일 우선.** 기본 레이아웃은 1열, 브레이크포인트에서 확장. 터치 타깃 ≥ 44px.
4. **라이트/다크 동등 지원.** 모든 색은 CSS 변수 토큰으로만 참조. 두 테마 모두 WCAG AA(본문 4.5:1, 큰 텍스트/UI 3:1) 충족.
5. **일관된 리듬.** radius·간격·타이포를 스케일로 고정, 한 번 정하면 컴포넌트가 따른다.

### 2.2 브랜드 톤
- **Primary 색상: Indigo 계열 유지** (기존 자산·인지 연속성). 단 하드코딩 대신 토큰화하고 명도를 AA에 맞춰 조정.
- 성격 키워드: **깨끗함 · 또렷함 · 차분한 신뢰**. 과한 그림자·그라디언트 지양, 얇은 보더 + 낮은 그림자 위주.
- 이모지 플레이스홀더(🛍️)는 이미지 도입 전까지 유지하되, 뉴트럴 `muted` 배경 위 중앙 정렬로 통일.

---

## 3. 디자인 토큰 (shadcn CSS 변수)

### 3.1 적용 방식 (Tailwind v4)
- 현재 `index.css`는 `@import 'tailwindcss'` 한 줄 + body 하드코딩. 아래로 교체.
- Tailwind v4는 `@theme inline`으로 CSS 변수를 유틸리티(`bg-background`, `text-foreground`, `border-border` 등)에 매핑한다.
- 다크 테마는 `.dark` 클래스 기반. `<html class="dark">` 토글(수동) + 초기값은 `prefers-color-scheme` 참조 권장.
- 색상 표기는 shadcn 최신 관례에 맞춰 **OKLCH**로 제시(가독·일관 명도). HSL이 편하면 동일 명도로 변환 가능.

```css
/* index.css */
@import 'tailwindcss';

@custom-variant dark (&:is(.dark *));

:root {
  /* 표면/텍스트 */
  --background: oklch(1 0 0);              /* #ffffff */
  --foreground: oklch(0.21 0.03 265);     /* slate-900 근사, 본문 텍스트 */
  --card: oklch(1 0 0);
  --card-foreground: oklch(0.21 0.03 265);
  --popover: oklch(1 0 0);
  --popover-foreground: oklch(0.21 0.03 265);

  /* 브랜드 */
  --primary: oklch(0.51 0.23 277);        /* indigo-600 근사, 흰 글자 대비 AA */
  --primary-foreground: oklch(0.98 0.01 277);

  /* 보조/뉴트럴 */
  --secondary: oklch(0.97 0.01 265);      /* slate-100 */
  --secondary-foreground: oklch(0.28 0.03 265);
  --muted: oklch(0.97 0.01 265);
  --muted-foreground: oklch(0.55 0.02 265); /* slate-500, 4.6:1 on bg */
  --accent: oklch(0.95 0.03 277);         /* 연한 인디고 틴트(호버/선택 배경) */
  --accent-foreground: oklch(0.32 0.12 277);

  /* 의미색 */
  --destructive: oklch(0.58 0.22 27);     /* red-600, 흰 글자 AA */
  --destructive-foreground: oklch(0.98 0.01 27);
  --success: oklch(0.55 0.15 152);        /* green-600 계열, 흰 글자 AA */
  --success-foreground: oklch(0.98 0.02 152);
  --warning: oklch(0.68 0.16 62);         /* amber-500 계열, 검은 글자 대비 */
  --warning-foreground: oklch(0.26 0.05 62);

  /* 라인/입력/포커스 */
  --border: oklch(0.92 0.01 265);         /* slate-200 */
  --input: oklch(0.92 0.01 265);
  --ring: oklch(0.51 0.23 277);           /* primary와 동일 계열 포커스 링 */

  --radius: 0.625rem;                     /* 10px 기준(카드 기본) */
}

.dark {
  --background: oklch(0.19 0.02 265);     /* slate-950 근사 */
  --foreground: oklch(0.97 0.01 265);
  --card: oklch(0.24 0.02 265);           /* 표면 한 단계 밝게(계층) */
  --card-foreground: oklch(0.97 0.01 265);
  --popover: oklch(0.24 0.02 265);
  --popover-foreground: oklch(0.97 0.01 265);

  --primary: oklch(0.68 0.16 277);        /* 다크에서 밝게 올려 대비 확보 */
  --primary-foreground: oklch(0.20 0.03 277);

  --secondary: oklch(0.28 0.02 265);
  --secondary-foreground: oklch(0.97 0.01 265);
  --muted: oklch(0.28 0.02 265);
  --muted-foreground: oklch(0.72 0.02 265); /* 다크 본문 보조, 4.5:1+ */
  --accent: oklch(0.32 0.05 277);
  --accent-foreground: oklch(0.92 0.03 277);

  --destructive: oklch(0.70 0.19 25);
  --destructive-foreground: oklch(0.20 0.03 25);
  --success: oklch(0.72 0.16 152);
  --success-foreground: oklch(0.20 0.03 152);
  --warning: oklch(0.80 0.15 74);
  --warning-foreground: oklch(0.26 0.05 62);

  --border: oklch(0.32 0.02 265 / 0.7);
  --input: oklch(0.32 0.02 265);
  --ring: oklch(0.68 0.16 277);
}

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-destructive-foreground: var(--destructive-foreground);
  --color-success: var(--success);
  --color-success-foreground: var(--success-foreground);
  --color-warning: var(--warning);
  --color-warning-foreground: var(--warning-foreground);
  --color-border: var(--border);
  --color-input: var(--input);
  --color-ring: var(--ring);
  --radius-sm: calc(var(--radius) - 4px);
  --radius-md: calc(var(--radius) - 2px);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) + 4px);
}

body { @apply bg-background text-foreground; }
```

> **대비 검증 원칙(구현 시 필수 확인):** 아래 조합은 AA를 목표로 값을 잡았다. react-expert는 최종 픽셀 값에서 대비를 재확인한다.
> - `foreground`/`background`: 라이트 ≈ 16:1, 다크 ≈ 15:1 (본문 AAA)
> - `muted-foreground`/`background`: 라이트·다크 모두 ≥ 4.5:1 (보조 텍스트 AA 본문)
> - `primary-foreground`/`primary`, `destructive-foreground`/`destructive`: ≥ 4.5:1
> - `ring`/`background`: ≥ 3:1 (비텍스트 UI, 포커스 인디케이터)
> - **주의:** 가격·매장명 등에 `muted-foreground`보다 옅은 색을 쓰지 말 것(기존 `slate-400`은 흰 배경에서 3:1 미만이라 본문 대비 실패 → 금지).

### 3.2 타이포 스케일
system UI 스택 유지(`system-ui, 'Segoe UI', Roboto, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif` — 한글 폴백 추가 권장).

| 토큰 | Tailwind | 크기/행간 | 용도 |
|---|---|---|---|
| Display | `text-3xl font-bold` | 30/36 | 페이지 최상위(상세 상품명 등 필요 시) |
| H1 | `text-2xl font-bold` | 24/32 | 상세 상품명, 페이지 제목 |
| H2 | `text-lg font-semibold` | 18/28 | 섹션 제목(인기 상품, 전체 상품) |
| Body | `text-sm` | 14/20 | 본문 기본(카드·목록) |
| Body-lg | `text-base` | 16/24 | 폼 입력값, 상세 설명 |
| Caption | `text-xs` | 12/16 | 매장명·메타·배지 |
| Price | `text-lg font-bold text-primary` | — | 가격 강조(상세는 `text-2xl`) |

- **가격은 항상 `text-primary` + bold**로 일관. 목록 카드/상세/장바구니 동일 규칙.
- 본문 최소 크기 12px(캡션) 이하 금지.

### 3.3 간격 · 반경 · 그림자
- **간격 스케일(4px 그리드):** 페이지 패딩 `px-4`(모바일)/`px-6`(md+), 섹션 간 `mb-8`, 카드 내부 `p-4`, 폼 필드 간 `space-y-4`(기존 `space-y-3`→`4`로 통일).
- **컨테이너 폭:** 기존 `max-w-5xl`은 상품 그리드에 다소 좁음 → 목록은 `max-w-7xl`, 폼/상세 텍스트 영역은 가독성 위해 `max-w-md`~`max-w-2xl` 유지.
- **Radius:** 카드/버튼 `rounded-lg`(=`--radius`), 배지/칩 `rounded-full`, 큰 히어로 이미지 `rounded-xl`. 임의 혼용 금지.
- **그림자:** 기본 카드 `shadow-sm`, 호버 `shadow-md`, 오버레이(dialog/sheet)만 `shadow-lg`. 그 이상 금지.

---

## 4. 사용할 shadcn 컴포넌트

도입 명령 기준(`npx shadcn@latest add ...`). Tailwind v4 / React 19 지원 버전 사용. 경로 별칭 `@/` 설정 필요(§7).

| 컴포넌트 | 사용 화면 | 대체 대상(현재) |
|---|---|---|
| `button` | 전 화면 CTA/보조 액션 | 모든 `<button className="bg-indigo...">` |
| `card` | 상품 카드, 장바구니 요약, 폼 컨테이너 | `rounded-xl border bg-white` 블록 |
| `input` | 검색·로그인·회원가입·수량 | 모든 `<input>` |
| `label` | 모든 폼 필드 | (신규 — D6 해결) |
| `badge` | 상품 상태, 판매수, 장바구니 개수, 비회원 태그 | 상태/판매 배지 |
| `select` | 상품 옵션 선택 | 네이티브 `<select>`(상세) |
| `dropdown-menu` | 헤더 계정 메뉴(로그인 시) | 인라인 nav 링크 뭉치 |
| `sheet` | 모바일 네비게이션 드로어 | (신규 — D12 해결) |
| `dialog` | 확인성 액션(장바구니 삭제 확인 등, 선택) | (신규) |
| `skeleton` | 목록/상세/장바구니 로딩 | "불러오는 중…" 텍스트(D4) |
| `separator` | 장바구니 요약, 헤더 구분 | `<span>|</span>`(Layout L46) |
| `sonner`(toast) | 담기/삭제/에러/로그인 피드백 | 인라인 `<p>` 메시지(D5) |
| `pagination` | 상품 목록 페이지 이동 | 수동 버튼 나열(D9) |
| `skeleton`+`aspect-ratio`(선택) | 상품 이미지 영역 | `aspect-square` div |
| `sonner` 외 `alert`(선택) | 폼 상단 에러 요약 | 인라인 에러 `<p>` |

> **최소 도입 세트(1차):** button, card, input, label, badge, select, skeleton, separator, sonner, sheet, dropdown-menu, pagination. dialog/alert는 2차.

---

## 5. 화면별 레이아웃 스펙

공통 상태 규약(모든 데이터 화면 적용):
- **로딩:** `skeleton`으로 실제 레이아웃과 동일한 형태·개수 표시(카드 그리드면 카드 스켈레톤 N개). 텍스트 "불러오는 중…" 폐기.
- **에러:** 페이지 레벨은 중앙 정렬 카드(아이콘 + 메시지 + "다시 시도" 버튼). 액션 레벨(담기 실패 등)은 `sonner` 토스트(`destructive`).
- **빈:** 일러스트/이모지 + 설명 + 다음 행동 CTA. 뉴트럴 톤.
- **성공(액션):** `sonner` 토스트(간결, 3초). 페이지 이동이 있는 경우 토스트 + 이동.

반응형 브레이크포인트(모바일 우선): 기본 < `sm`(640) < `md`(768) < `lg`(1024).

---

### 5.1 공통 레이아웃 — `Layout.tsx`

**구조**
```
header (sticky top-0, bg-background/95 backdrop-blur, border-b border-border, h-14~16)
  └ container(max-w-7xl, px-4 md:px-6)
     ├ 로고(좌): "마켓" text-lg font-bold text-primary
     ├ [데스크톱 md+] 인라인 nav (상품 / 장바구니 / 주문조회|내주문 …)
     ├ [우측 항상] 액션 영역:
     │    ├ 장바구니 아이콘 버튼 + Badge(개수)  ← 신규
     │    ├ 테마 토글 버튼(라이트/다크)          ← 신규
     │    └ 로그인 상태: DropdownMenu(이름 ▾ → 내주문/쿠폰·포인트/판매자/[관리자]/로그아웃)
     │       비로그인: "로그인" Button(size sm) + "주문조회" 링크
     └ [모바일 < md] 좌측 Sheet 트리거(햄버거) → 네비 항목 세로 나열
main (flex-1, container, py-6 md:py-8)
footer (border-t, muted-foreground, text-xs, py-6)
```

**컴포넌트 매핑**
- 네비 항목: `NavLink` 유지하되 활성 스타일을 토큰화 — 활성 `text-primary font-semibold`, 비활성 `text-muted-foreground hover:text-foreground`.
- 계정 메뉴: `dropdown-menu` (로그인 시 nav 항목 과밀 D12 해소).
- 모바일 네비: `sheet`(좌측), 트리거는 `button variant=ghost size=icon` + `aria-label="메뉴 열기"`.
- 장바구니 배지: `badge`(우상단 오버레이, 개수 0이면 미표시). 개수는 회원=cart API, 게스트=guestCart length. `aria-label="장바구니, N개"`.
- 구분선: `separator`(orientation vertical) 또는 제거.
- 테마 토글: `button variant=ghost size=icon`, `aria-label="테마 전환"`, `<html class>` 토글 + localStorage 저장.

**상태**
- 헤더는 데이터 의존 없음(auth context). 장바구니 배지 로딩 중엔 배지 숨김(플리커 방지).

**반응형**
- < md: 인라인 nav 숨김 → Sheet. 로고 중앙 또는 좌측, 우측 장바구니+메뉴 아이콘.
- ≥ md: 인라인 nav 노출, Sheet 트리거 숨김.

---

### 5.2 상품 목록 — `ProductListPage.tsx` + `ProductCard.tsx`

**페이지 구조(모바일 우선)**
```
1) 검색 바 (form): Input(flex-1, aria-label="상품명 검색") + Button "검색"
2) 카테고리 필터: 가로 스크롤 칩 줄 (Badge/Button pill, role="tablist" 또는 group)
     - "전체" + 카테고리들, 선택 시 variant=default(primary), 비선택 variant=outline
     - 스크롤 영역에 좌우 페이드 마스크(선택)
3) [기본뷰 한정] 인기 상품 섹션: H2 "인기 상품" + grid
     - grid-cols-2 / sm:grid-cols-3 / lg:grid-cols-4
     - 카드에 "N개 판매" Badge(variant=secondary, 우상단)
4) 전체 상품 섹션: H2 + grid (grid-cols-2 / sm:grid-cols-3 / lg:grid-cols-4)
5) 페이지네이션: shadcn Pagination (이전/다음 + 현재 주변 페이지 + 말줄임)
```

**ProductCard 스펙**
```
Card (링크 전체 클릭, hover:shadow-md transition, focus-visible:ring)
  ├ 이미지 영역: aspect-square, bg-muted, rounded-md, 중앙 이모지(이미지 없을 때)
  │    └ 상태 배지(우상단): ON_SALE이면 미표시,
  │         SOLD_OUT → Badge variant=destructive "품절"
  │         DRAFT → Badge variant=secondary "준비중"
  │         HIDDEN → 목록 노출 안 됨(있으면 secondary)
  │    └ (인기) 판매수 배지(좌상단 or 우상단): variant=secondary
  ├ 매장명: text-xs text-muted-foreground truncate
  ├ 상품명: text-sm font-medium truncate (2줄 허용 시 line-clamp-2)
  └ 가격: text-base font-bold text-primary
```
- 카드 전체가 링크: `<a>`로 감싸되 내부 배지는 텍스트 콘텐츠. 접근성 이름 = 상품명+가격.
- **D8 해결:** 상태별 색 배지로 위계 부여(품절=destructive, 준비중=secondary).

**상태**
- 로딩: `ProductCardSkeleton`(이미지+2줄 텍스트+가격 바) 그리드로 8개.
- 에러: 중앙 카드 + "다시 시도"(재요청).
- 빈: "조건에 맞는 상품이 없습니다." + (검색/필터 있으면) "필터 초기화" 버튼.
- 인기 상품 없음: 섹션 자체 미표시(현행 유지).

**페이지네이션(D9 해결)**
- `pagination` 컴포넌트. 표시 규칙: 첫/현재±1/마지막 + 사이 `…`. 모바일에선 "이전 / n/N / 다음" 축약.
- 현재 페이지 `aria-current="page"`.

**반응형**
- 그리드: 2 → sm:3 → lg:4 열. 카테고리 칩 줄은 항상 가로 스크롤(`overflow-x-auto`, 스크롤바 숨김).

---

### 5.3 상품 상세 — `ProductDetailPage.tsx`

**구조**
```
grid (기본 1열, md:2열, gap-8)
 ├ 좌: 이미지 영역 (aspect-square, bg-muted, rounded-xl, 중앙 이모지)
 └ 우: 구매 정보 (sticky top-20 on lg 선택)
     ├ 매장명 (text-sm text-muted-foreground)
     ├ 상품명 H1 (text-2xl font-bold)
     ├ 상태 배지 (ON_SALE 외에만; 색 규칙 §5.2)
     ├ 가격 (text-2xl font-bold text-primary) — 옵션 선택 시 옵션가 반영
     ├ 설명 (text-sm text-muted-foreground, whitespace-pre-line)
     ├ Separator
     ├ 옵션 선택: Label "옵션" + Select
     │     - 품절 옵션 disabled + "(품절)" 접미
     ├ 수량: Label "수량" + [− Input(number) +] Stepper
     │     - 버튼 size ≥ 44px(h-11 w-11), aria-label "수량 감소/증가"
     │     - max = 선택 옵션 재고
     └ CTA: Button size=lg w-full
           - 구매 가능: "장바구니 담기" (variant=default)
           - 불가: disabled "구매할 수 없는 상품"
```

**상태**
- 로딩: 이미지 스켈레톤 + 우측 텍스트 스켈레톤(제목/가격/옵션 자리).
- 에러(상품 자체 로드 실패): 페이지 중앙 카드 + "목록으로".
- 담기 성공/실패: **`sonner` 토스트**로 전환(D5). 성공 토스트에 "장바구니 보기" 액션 버튼 포함 권장.
- 비회원 담기: 성공 토스트에 "(비회원 장바구니에 담김)" 보조 문구.
- 품절/판매중지: CTA disabled + 상태 배지.

**반응형**
- < md: 1열(이미지 → 정보). CTA는 하단 sticky 바로 고정(모바일 전환 향상, 선택). 
- ≥ md: 2열.

---

### 5.4 장바구니 — `CartPage.tsx`

**구조**
```
H1 "장바구니" (+ 게스트면 Badge variant=outline "비회원")
grid (기본 1열, lg:3열)
 ├ 좌(lg col-span-2): 항목 리스트
 │   각 항목 = Card (flex, gap-4, p-4)
 │     ├ 썸네일 (h-16 w-16, bg-muted, rounded-md)
 │     ├ 정보(flex-1): 상품명(text-sm font-medium) / 옵션명(text-xs muted) / 단가(text-primary)
 │     │     └ 재고부족 시: text-xs text-destructive "재고 부족(가용 N)"
 │     ├ 수량 Stepper: [− qty +] (버튼 h-11 w-11, aria-label)
 │     └ 삭제: Button variant=ghost size=icon (아이콘 휴지통) aria-label="삭제"
 └ 우(lg col-span-1): 요약 Card (sticky top-20)
     ├ 총 수량 (justify-between, muted)
     ├ Separator
     ├ 합계 (font-bold, 값 text-primary)
     ├ Button size=lg w-full "주문하기" (재고부족 항목 있으면 disabled)
     └ 게스트 안내 (text-xs muted): "로그인 시 장바구니가 병합됩니다."
```

**상태**
- 로딩: 항목 스켈레톤 3개 + 요약 스켈레톤.
- 빈: 중앙 정렬 — 이모지 + "장바구니가 비어 있습니다." + Button "상품 보러 가기".
- 에러: 상단 `alert`(destructive) 또는 토스트. 수량 변경 실패는 토스트.
- 수량 변경 중: 해당 항목 stepper `disabled`(중복 요청 방지) — 현재 없음, 추가 권장.
- 삭제: 확인 없이 즉시 + 토스트 "삭제했습니다" (undo 액션 있으면 이상적, 2차).

**D7 해결:** 수량/삭제 버튼 44px 이상.

**반응형**
- < lg: 1열(리스트 → 요약). 요약을 하단 sticky 바로 고정(총액+주문하기) 권장.
- ≥ lg: 2:1 그리드, 요약 sticky.

---

### 5.5 로그인 / 회원가입 — `LoginPage.tsx`, `SignupPage.tsx`

**구조(공통)**
```
중앙 정렬 (min-h, place-items-center), Card max-w-sm w-full
  ├ CardHeader: 제목(로그인/회원가입) + 짧은 설명(선택)
  ├ CardContent: form (space-y-4)
  │    각 필드 = <div> Label(htmlFor) + Input(id) [+ 도움말/에러 텍스트]
  │      - 로그인: 이메일 / 비밀번호
  │      - 회원가입: 이름 / 이메일 / 비밀번호(도움말 "8~64자")
  │    - 에러: 필드 하단 text-xs text-destructive + Input aria-invalid
  │    - 폼 레벨 에러: 상단 alert(destructive) 또는 토스트
  │    - 제출: Button type=submit w-full, 로딩 시 disabled + 스피너 + "로그인 중…"
  └ CardFooter: 전환 링크(계정 없음→회원가입 / 있음→로그인)
```

**D6 해결(핵심):** 모든 Input에 `<Label htmlFor>` + `id` 연결. placeholder는 보조로만.
- 이메일 `type=email autoComplete=email`, 비밀번호 로그인 `autoComplete=current-password` / 가입 `new-password`, 이름 `autoComplete=name`.
- 필수 표시: Label에 시각적 `*` + `aria-required`.

**상태**
- 제출 중: 버튼 disabled + 로딩 텍스트(현행 로직 유지).
- 실패: 인라인/토스트 에러(비밀번호 필드 초기화하지 말 것 — 재입력 부담).
- 성공: 리다이렉트(현행).

**반응형**
- 단일 컬럼, `max-w-sm`. 모바일에서 상하 여백 축소(`py-8`).

---

### 5.6 리텐션 화면 (찜·등급·알림함) — 확장 기능

MVP 리디자인(§5.1~§5.5) 이후 추가된 커머스 스위트·리텐션 화면. 위 토큰·컴포넌트 체계를 그대로 따르며,
아래는 각 화면 고유의 스펙만 짚는다(공통 상태 규약·접근성은 §5 서두·§6 준수).

**하트(찜) 토글 — `WishlistButton`** (상품 카드 우상단 / 상세 제목 옆)
- `button variant=ghost size=icon`, 아이콘 하트(빈/채움). `aria-label`은 "찜하기"/"찜 해제".
- 상태: 찜됨 = 채운 하트 `text-primary`(또는 `text-destructive` 계열 중 택1, 전 화면 일관), 미찜 = 빈 하트 `text-muted-foreground`.
- 비로그인 클릭 → `sonner` 토스트로 로그인 유도(모달/리다이렉트). 카드 링크 클릭과 이벤트 전파 분리(`stopPropagation`).
- 최소 터치 타깃 44px(카드 위 오버레이여도 히트박스 확보).

**찜한 상품 — `MyWishlistPage`** (마이페이지 탭)
- 상품 그리드(§5.2 카드 재사용). 각 카드에 **가격 인하 배지**: `badge variant=destructive` "▼ 15% 인하"
  + 원가(취소선 `text-muted-foreground line-through`) / 현재가(`text-primary font-bold`).
- 상단 필터: "가격 인하만 보기" 토글(`switch` 또는 outline 토글 버튼).
- 품절/판매중지: §부록A 배지 규칙. 항목별 찜 해제·장바구니 담기 액션.

**내 등급 — `MyLoyaltyTierPage`** (마이페이지 탭)
- 등급 카드: 상단 등급 뱃지(§부록A 등급 색), 최근 12개월 순구매액, **다음 등급까지 진행바**
  (`progress`, 남은 금액 캡션), 산정 시각(`text-xs text-muted-foreground`).
- "등급 전용 쿠폰" 혜택 안내 문구(가격 배수 아님을 오인하지 않게 담백하게).

**알림함 — `NotificationBell` / `NotificationsPage`**
- 벨 아이콘 + 안읽음 개수 `badge`(개수 0이면 미표시), `aria-label="알림, N개 안읽음"`.
- 각 알림 앞에 **타입별 아이콘**(재입고/가격인하/카트리마인드/멤버십/정기배송). 제목·본문·이동은 서버값
  (`title`/`body`/`linkUrl`) 그대로 — 프론트는 아이콘만 분기.
- 읽음/안읽음 시각 위계: 안읽음 배경 `bg-accent/40`, 읽음 뉴트럴.

---

## 6. 접근성 체크리스트 (WCAG 2.1 AA)

react-expert가 각 컴포넌트 완료 시 확인.

### 6.1 색·대비
- [ ] 본문 텍스트(`foreground`/`background`) ≥ 4.5:1 — 라이트/다크 모두.
- [ ] 보조 텍스트(`muted-foreground`) ≥ 4.5:1. **기존 `slate-400` 사용 전면 금지**(가격·매장명 포함).
- [ ] 큰 텍스트(≥18.66px bold/24px) 및 UI 컴포넌트·경계 ≥ 3:1.
- [ ] 상태를 색으로만 전달하지 않기 — 배지에 텍스트("품절"·"준비중") 병행(색맹 고려).
- [ ] 포커스 링(`ring`) 대 배경 ≥ 3:1.

### 6.2 포커스 / 키보드 (D10)
- [ ] 모든 인터랙티브 요소 `focus-visible:ring-2 ring-ring ring-offset-2` (shadcn 기본 제공).
- [ ] 탭 순서 논리적(헤더 → 필터 → 목록 → 페이지네이션).
- [ ] Sheet/Dialog 열림 시 포커스 트랩 + ESC 닫힘 + 닫을 때 트리거로 복귀(shadcn 기본).
- [ ] 카드 링크 키보드 접근 가능, Enter 활성.
- [ ] 커스텀 stepper 키보드 조작 가능.

### 6.3 시맨틱 / ARIA (D5, D11)
- [ ] 페이지당 `<h1>` 하나, 섹션 `<h2>` 위계 유지.
- [ ] 랜드마크: `header`/`nav`/`main`/`footer` (현행 유지).
- [ ] 토스트/라이브 영역 `aria-live`(sonner 기본 `role=status`).
- [ ] 아이콘 전용 버튼 전부 `aria-label`(장바구니, 삭제, 수량 ±, 메뉴, 테마).
- [ ] 장바구니 배지 개수 `aria-label="장바구니, N개"`.
- [ ] 페이지네이션 현재 페이지 `aria-current="page"`.
- [ ] 카테고리 칩 그룹 역할 부여(tablist/group), 선택 상태 전달.

### 6.4 폼 (D6)
- [ ] 모든 입력에 연결된 `<label>`(`htmlFor`/`id`). placeholder를 라벨 대용으로 쓰지 않기.
- [ ] 에러 메시지 `aria-describedby`로 입력과 연결, `aria-invalid` 설정.
- [ ] `autoComplete` 속성 지정(§5.5).
- [ ] 필수 필드 `required` + 시각 표시.

### 6.5 터치 타깃 / 모바일 (D7)
- [ ] 모든 탭 가능한 컨트롤 ≥ 44×44 CSS px(수량 ±, 페이지네이션, 아이콘 버튼).
- [ ] 인접 타깃 간 충분한 간격(≥ 8px).
- [ ] 가로 스크롤 영역(카테고리) 외 본문 가로 스크롤 없음.
- [ ] `prefers-reduced-motion` 존중 — transition 최소화 대응.

### 6.6 테마
- [ ] 초기 테마 = `prefers-color-scheme` 반영, 사용자 선택 localStorage 유지.
- [ ] 테마 전환 시 FOUC 방지(초기 인라인 스크립트로 `.dark` 선반영).
- [ ] 라이트/다크 각각 위 대비 항목 재검증.

---

## 7. 도입 사전 작업 (react-expert 착수 체크)

1. **경로 별칭 `@/`** — 현재 미설정(태스크 명시). `vite.config.ts`에 `resolve.alias`, `tsconfig`에 `paths` 추가. shadcn CLI 전제 조건.
2. **`components.json`** — `npx shadcn@latest init` (style: new-york 권장, base color: slate, css variables: yes).
3. **의존성** — shadcn가 요구하는 `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`, `tailwindcss-animate`(v4는 `tw-animate-css`) 자동 추가.
4. **`index.css` 교체** — §3.1 토큰 블록으로.
5. **`sonner` `<Toaster/>`** — `App.tsx` 루트에 1회 마운트.
6. **폰트** — 한글 폴백(`'Apple SD Gothic Neo','Noto Sans KR'`) 스택에 추가.

---

## 부록 A. 상태 배지 색 매핑(단일 소스)

| 대상 | 값 | Badge variant | 라벨 |
|---|---|---|---|
| 상품 | ON_SALE | (미표시) | 판매중 |
| 상품 | SOLD_OUT | destructive | 품절 |
| 상품 | DRAFT | secondary | 준비중 |
| 상품 | HIDDEN | secondary | 숨김 |
| 인기 | soldQuantity | secondary | N개 판매 |
| 장바구니 | !purchasable | destructive(텍스트) | 재고 부족 |
| 계정 | 비회원 | outline | 비회원 |
| 찜 | isPriceDropped | destructive | ▼ N% 인하 |
| 로열티 | BRONZE | secondary(뉴트럴 브론즈 톤) | 브론즈 |
| 로열티 | SILVER | secondary(실버 톤) | 실버 |
| 로열티 | GOLD | warning 계열(골드 톤) | 골드 |
| 로열티 | VIP | primary(강조) | VIP |

주문 상태(주문 관련 화면은 1차 범위 밖이나 일관성 위해 기록): CREATED=warning, PAID=success, SHIPPED/DELIVERED=default/success, CANCELED=destructive.

알림 타입 아이콘(단일 소스, `labels.ts` 매핑): RESTOCK=📦, PRICE_DROP=🔻, CART_REMINDER=🛒, MEMBERSHIP=⭐, DELIVERY_SUBSCRIPTION=🔁, GIFT=🎁. 색으로만 구분하지 말고 아이콘+텍스트 병행(§6.1).
