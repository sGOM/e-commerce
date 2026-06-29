# 마켓플레이스 프론트엔드 (고객 스토어프론트)

백엔드(Spring Boot) API 위에서 동작하는 고객용 SPA. React 19 + Vite + TypeScript + Tailwind CSS v4.

## 화면
- 상품 목록/검색, 상품 상세(옵션 선택·장바구니 담기)
- 장바구니(수량 변경·삭제), 주문/결제(Mock PG 즉시 승인)
- 내 주문 목록·상세(주문 취소), 로그인/회원가입

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
├── components/ Layout, ProtectedRoute
├── pages/      상품/장바구니/결제/주문/로그인 화면
└── labels.ts   상태 enum → 한국어 라벨
```
