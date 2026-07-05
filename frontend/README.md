# 마켓플레이스 프론트엔드 (고객 스토어프론트)

백엔드(Spring Boot) API 위에서 동작하는 고객용 SPA. React 19 + Vite + TypeScript + Tailwind CSS v4.

## 화면
- **고객**: 상품 목록(인기 상품 섹션·키워드 검색·카테고리 필터)·상세(옵션 선택·리뷰·찜), 컬렉션/플래시세일, 장바구니(회원 서버/게스트 localStorage), 주문/결제(Mock PG·배송슬롯), 내 주문·취소, 쿠폰/포인트, **찜한 상품·가격 인하 알림**, **로열티 등급**, 멤버십, 정기배송, 선물하기/기프트 클레임, **인앱 알림함**, 로그인/회원가입
- **게스트**: localStorage 장바구니, 비회원 주문, 주문번호+연락처 조회, 로그인 시 장바구니 병합·주문 연결(claim)
- **판매자**(`/seller`): 입점 신청, 상품 등록·재고 조정, 판매분 주문·송장 등록, 정산 내역, 플래시세일 신청
- **관리자**(`/admin`): 셀러 심사, 전체 주문 검색·환불, 쿠폰 발행·카테고리 등록, 정산 생성·지급·수수료율, 컬렉션·플래시세일 편성, 배송 권역/슬롯, 멤버십, 리뷰 검수, 로열티 등급 조회·재계산, 카트 리마인드 발송

> 알림함은 범용(`Notification`) 설계라 재입고·가격인하·카트리마인드 등이 타입별 아이콘으로 한 화면에 섞여 노출된다. 진행 중인 **shadcn/ui + Tailwind v4 디자인 토큰 마이그레이션**(라이트/다크)은 `../docs/design/storefront-ui-spec.md` 참조.

## 백엔드 연동
- Vite dev 프록시로 `/api` → `http://localhost:8080` (동일 출처 → 세션 쿠키/CSRF 그대로 흐름)
- 인증: 세션 기반. 변경 요청은 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 전송(`src/api/client.ts`)
- 공통 응답 `{ success, code, message, data }` 를 클라이언트에서 언랩하고 실패 시 `ApiError` 로 변환

## 실행
```bash
# 1) 백엔드 먼저 기동 (저장소 루트)
./gradlew bootRun                 # http://localhost:8080

# 2) 프론트 dev 서버
cd frontend
npm install
npm run dev                       # http://localhost:5173
```

## 구조
```
src/
├── api/        client(fetch+CSRF), types, endpoints
├── auth/       AuthContext (세션 기반 로그인 상태)
├── hooks/      useWishlist (찜 상태 전역 컨텍스트) 등
├── components/ Layout, ProtectedRoute, ProductCard, WishlistButton, NotificationBell …
├── pages/      상품/장바구니/결제/주문/로그인 + 찜·등급·멤버십·정기배송·선물하기·알림함 + seller/* + admin/*
└── labels.ts   상태 enum → 한국어 라벨(등급 뱃지·알림 아이콘 포함)
```
