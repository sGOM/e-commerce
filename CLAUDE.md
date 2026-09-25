# CLAUDE.md

B2C 마켓플레이스 이커머스. 백엔드 Kotlin/Spring Boot 3.5(`src/`, 패키지 `com.example.starter`) + 프론트 React 19/Vite/TS(`frontend/`).
이 파일은 매 세션 로드되므로 **규칙과 지도만** 둔다. 설명은 링크한 문서에 있다.

## 문서 지도 (필요할 때 해당 문서만 읽는다)

| 알고 싶은 것 | 문서 |
|---|---|
| 남은 작업·막힌 이유·진행 순서 | [`docs/ROADMAP.md`](docs/ROADMAP.md) — **작업 선택의 출발점** |
| 기능의 "무엇을·왜"·수용기준 | [`docs/planning/`](docs/planning/README.md) |
| 동시성·금액·결제·정산 등 비CRUD 구현 원리 | [`docs/SERVER_ARCHITECTURE.md`](docs/SERVER_ARCHITECTURE.md) |
| 제품 요구사항·확정된 정책 결정 | [`PRD.md`](PRD.md) |
| **코드 작성 규칙(레이어·트랜잭션·예외·DTO·테스트 모양)** | [`docs/CODING_CONVENTIONS.md`](docs/CODING_CONVENTIONS.md) — 코드 작성 전 해당 절을 읽는다 |
| 브랜치·커밋·PR 규칙 | [`docs/GIT_CONVENTIONS.md`](docs/GIT_CONVENTIONS.md) |
| UI 토큰·컴포넌트 규칙 | [`docs/design/storefront-ui-spec.md`](docs/design/storefront-ui-spec.md) |
| 전체 API 목록 | 앱 기동 후 `/swagger-ui/index.html` (README 표는 핵심만) |

## 명령

```bash
./gradlew compileKotlin          # 빠른 백엔드 검증
./gradlew test                   # 전체 테스트(Testcontainers PostgreSQL — Docker 필요)
./gradlew test --tests '*OrderConcurrencyIntegrationTest*'  # 단일 테스트
./gradlew ktlintFormat           # Kotlin 포맷(규칙: .editorconfig) — 커밋 전 필수
cd frontend && npm run format    # 프론트 포맷(prettier). Edit/Write 한 파일은 훅이 자동 포맷한다
cd frontend && npm test && npm run lint && npm run build   # 프론트 검증
```

CI(`.github/workflows/ci.yml`)는 `ktlintCheck` + 백엔드 테스트, 프론트 lint/`format:check`/test/build(`tsc -b` 타입 체크 포함)를 돌린다.
백엔드 테스트에는 코드↔문서 일관성 검사(`ConsistencyTest` — 컨트롤러 역할 분리, DTO 위치, 설정 키의 SERVER_ARCHITECTURE §11 기재)가 포함된다.

## 반드시 지킬 불변식

- **스키마**: Flyway 가 단일 관리(`src/main/resources/db/migration/V*.sql`). 기존 마이그레이션 수정 금지, 새 버전 파일만 추가. `ddl-auto: validate`.
- **금액**: 전부 서버 계산. 클라이언트 금액 신뢰 금지. KRW `Long`, 비율은 basis point(100 = 1%).
- **재고**: 예약/해제는 `InventoryRepository.reserve/release` 의 조건부 원자적 UPDATE 만 쓴다. 락·읽고-쓰기 금지.
- **운영값**: 적립률·수수료율 같은 정책값은 코드 상수가 아니라 DB 정책 행.
- **외부 연동**: 인터페이스 + `@ConditionalOnProperty`/설정 유무로 실제/로컬 구현을 고른다(`PaymentGateway`, `EmailSender`). 키가 없어도 앱은 부팅되고 테스트는 돈다.
- **응답**: 공통 봉투 `{ success, code, message, data }`, 오류는 `ErrorCode` + `BusinessException`.
- **프론트 API 호출**: `frontend/src/api/client.ts` 래퍼만 사용(CSRF 헤더·응답 언랩). 새 API 는 `endpoints.ts` 에 타입드 함수로 추가. tsc `noUnusedLocals` 가 엄격하다.

## 작업 흐름

1. `docs/ROADMAP.md` 에서 항목 선택 → `main` 최신에서 `<type>/<slug>` 브랜치.
2. TDD: 실패하는 테스트 먼저(통합 테스트는 `AbstractIntegrationTest` 상속, 외부 빈은 `@MockkBean`).
   - 통합 테스트는 DB 컨테이너를 공유한다. 롤백 검증 등으로 `@Transactional` 을 뺀 테스트는 데이터가 커밋되므로
     정산 대상 상태(PAID 등) 주문을 남기지 않게 정리한다(정산 테스트가 전역 집계). 정리는 **자기가 만든 행만** —
     `deleteAll()` 같은 전역 삭제는 다른 테스트 데이터의 FK 에 걸려 실행 순서에 따라 깨진다.
   - 배치의 건별 격리는 **별도 빈 + `REQUIRES_NEW`** 로 한다(같은 클래스 자기 호출엔 `@Transactional` 미적용).
3. Kotlin 을 고쳤다면 `./gradlew ktlintFormat`, 프론트를 고쳤다면 `cd frontend && npm run format` 후 백엔드 전체 테스트 + 프론트 test/lint/build 통과 확인. [`CODING_CONVENTIONS.md` §5 자가 점검](docs/CODING_CONVENTIONS.md#5-pr-전-자가-점검)을 훑는다.
4. 커밋은 GIT_CONVENTIONS 규칙(원자적, 마이그레이션은 사용 코드와 같은 커밋, 비자명한 결정은 본문에 "왜"+공식 문서 링크).
5. `gh pr create --base main` → CI green 확인 → `gh pr merge --merge` → ROADMAP 완료 반영.
   - 열린 PR 에 추가 push 하면 `gh pr checks --watch` 가 새 실행 등록 전 "no checks reported" 로 실패할 수 있다.
     `gh run list --branch <b> --json databaseId,headSha` 로 새 run 을 찾아 `gh run watch <id> --exit-status`.

**멈추고 사용자에게 물을 것**: 구현 방향이 갈리는 정책 결정(ROADMAP "막힌 이유"의 정책 행), 실제 시크릿·실결제 등 되돌리기 어려운 외부 영향.
"외부 키가 필요하다"는 대개 런타임 설정 문제일 뿐 막힘이 아니다 — 코드와 계약 테스트는 먼저 끝낸다.

## 서브에이전트

`.claude/agents/` — `spring-expert`(백엔드), `react-expert`(프론트), `uiux-expert`(디자인 판단), `product-planner`(기획서).
작은 변경은 직접 하고, 독립적인 큰 작업만 위임한다. 코드 규칙의 원천은 에이전트 정의가 아니라 `CODING_CONVENTIONS.md` 하나다(중복 기재 금지).

`.claude/settings.json` 훅: Edit/Write 한 `frontend/src` 의 ts/tsx/css 를 prettier 로 자동 포맷(`.claude/hooks/format-edited-file.cjs`). **Bash(sed·스크립트)로 고친 파일은 훅 대상이 아니다** — 3단계의 포맷 명령으로 맞춘다.
