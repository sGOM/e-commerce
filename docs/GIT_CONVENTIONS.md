# Git 컨벤션 가이드

이 문서는 이 저장소에서 **브랜치 / 커밋 / PR을 어떤 기준으로 나누고 작성하는지**를 정의한다.
사람과 서브에이전트(spring-expert, react-expert 등)가 동일한 규칙으로 이력을 쌓아, **나중에 버그를 추적하고 되돌리기 쉬운 히스토리**를 만드는 것이 목적이다.

핵심 원칙 세 가지:

1. **버그 추적이 용이한 단위로 쪼갠다** — 하나의 커밋 = 하나의 논리적 변경(atomic commit). `git bisect`·`git revert`가 의미 있게 동작해야 한다.
2. **기능(도메인) 단위로 브랜치·커밋을 묶는다** — `type(scope)`의 scope가 곧 도메인이다.
3. **일반 지식을 벗어난 부분은 반드시 설명을 남긴다** — 단순 CRUD/API가 아니라 한눈에 이해하기 어려운 결정·트릭·동시성/트랜잭션 처리는 커밋 본문 또는 PR 본문에 "왜"를 적고 가능하면 공식 문서를 링크한다.

---

## 1. 커밋 단위: "버그 추적이 용이한 단위"

### 1.1 원자적 커밋(Atomic Commit)

한 커밋은 **그것만 떼어내도(cherry-pick) / 되돌려도(revert) 저장소가 깨지지 않는** 최소한의 완결된 변경이어야 한다.

- ✅ **된다**: "쿠폰 발급 API 추가"(엔티티 + 마이그레이션 + 컨트롤러 + 테스트가 한 커밋)
- ❌ **안 된다**: "작업 중간 저장", "오타 수정 + 리뷰 기능 추가 + 리팩터링"이 한 커밋에 뒤섞임

> **왜 중요한가 (일반 지식을 벗어난 부분)**
> `git bisect`는 "정상 커밋"과 "버그 커밋" 사이를 이분탐색하여 버그를 처음 심은 커밋을 찾아준다. 이때 **각 커밋이 그 자체로 빌드·테스트가 통과하는 완결 상태**여야 이분탐색이 유효하다. "중간 저장" 커밋이 섞이면 그 커밋에서 컴파일이 깨져 bisect가 무의미해진다.
> 참고: [git bisect 공식 문서](https://git-scm.com/docs/git-bisect), [Pro Git 7.1 Revision Selection](https://git-scm.com/book/en/v2/Git-Tools-Debugging-with-Git#_binary_search)

### 1.2 커밋을 나누는 기준

| 상황 | 커밋 분리 방식 |
|---|---|
| 리팩터링 + 신기능 | **리팩터링 먼저 별도 커밋**, 그 위에 신기능 커밋. (리뷰어가 "동작 불변 변경"과 "동작 변경"을 구분) |
| 도메인 A + 도메인 B 동시 수정 | scope가 다르면 원칙적으로 커밋 분리 (`feat(order):` / `feat(payment):`) |
| 기능 + 그 기능의 테스트 | **같은 커밋**에 포함 (기능과 검증은 한 완결 단위) |
| DB 마이그레이션 + 이를 쓰는 코드 | **같은 커밋** (마이그레이션만 먼저 머지되면 스키마-코드 불일치 발생) |
| 포맷팅/오타 | 로직 커밋과 **절대 섞지 않는다**. 별도 `chore`/`style` 커밋 |

### 1.3 되돌리기 가능성 확보

- 결제·정산·재고처럼 **되돌리기가 위험한 도메인**은 특히 커밋을 잘게 나눠, 문제 시 `git revert <sha>`로 해당 변경만 정확히 걷어낼 수 있게 한다.
- 이미 push된 공유 브랜치는 `git reset`/`--force`로 역사를 지우지 말고 `git revert`로 "취소 커밋"을 쌓는다(추적 이력 보존).

---

## 2. 커밋 메시지: Conventional Commits

이 저장소는 [Conventional Commits 1.0.0](https://www.conventionalcommits.org/ko/v1.0.0/)을 따른다.

### 2.1 형식

```
<type>(<scope>): <제목 — 명령형, 소문자 시작, 마침표 없음>

<본문 — 무엇을 왜 바꿨는지. 특히 "왜". 줄당 72자 권장>

<footer — BREAKING CHANGE / 이슈 참조 / Co-Authored-By>
```

실제 이력 예시(이 저장소):

```
feat(order): multi-seller orders with SubOrder split and atomic stock
feat(coupon-point): discount coupons and FIFO point ledger with expiry
feat(payment): complete Toss PG confirm integration
docs: rewrite README for the e-commerce marketplace platform
```

### 2.2 type

| type | 용도 |
|---|---|
| `feat` | 사용자에게 보이는 신규 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 불변, 내부 구조 개선 |
| `perf` | 성능 개선 |
| `docs` | 문서만 변경 |
| `test` | 테스트 추가/수정만 |
| `chore` | 빌드·의존성·설정 등 |
| `style` | 포맷팅(로직 변화 없음) |

### 2.3 scope = 도메인 (요청 원칙 #2 "기능 별")

scope는 **변경이 속한 도메인/영역**을 적는다. 이 저장소에서 실제 쓰는 scope:

`catalog` · `order` · `cart` · `payment` · `coupon-point` · `settlement` · `seller` · `frontend`

- 새 기획(`docs/planning/`)을 구현할 땐 해당 기능 slug에 맞는 scope를 쓴다. 예: 리뷰 → `feat(review):`, 정기배송 → `feat(subscription):`.
- **한 커밋의 scope는 하나**가 원칙. 두 도메인을 건드려야 하면 커밋을 나눈다(§1.2).

### 2.4 제목(subject) 작성 규칙

- 명령형 현재시제: "add", "fix" (O) / "added", "adds" (X)
- 왜 명령형인가: git 자체가 만드는 커밋("Merge branch…")과 문법을 맞춰 이력을 읽기 쉽게 하기 위함. 참고: [Git 프로젝트 커밋 가이드라인](https://git-scm.com/docs/SubmittingPatches#describe-changes)

### 2.5 본문(body): "왜"를 남긴다 (요청 원칙 #3)

제목은 "무엇"을, **본문은 "왜"와 "어떻게"**를 담는다. 아래에 해당하면 본문을 **반드시** 작성한다:

- 동시성/트랜잭션/락 처리 (예: 재고 원자적 UPDATE, 낙관/비관 락 선택 이유)
- 금액·정산·세금 등 **되돌리기 어렵고 정합성이 중요한** 계산 로직
- 비직관적 트레이드오프, 성능 튜닝, 우회(workaround)
- 외부 스펙(PG사, OAuth2 등)에 강제된 구현

이때 **공식 문서/스펙 링크를 함께 남긴다.** 예:

```
feat(order): reserve stock with atomic conditional UPDATE

낙관/비관 락 대신 단일 원자적 UPDATE로 초과판매를 막는다:
  UPDATE inventories SET reserved = reserved + :qty
   WHERE option_id = :id AND quantity - reserved >= :qty
영향 행이 0이면 재고 부족으로 판단. 비관적 락은 다중 셀러
동시 주문에서 락 경합·데드락이 잦아 배제.

참고: https://www.postgresql.org/docs/current/transaction-iso.html
```

### 2.6 BREAKING CHANGE / 이슈 참조 (footer)

- 하위호환을 깨면 footer에 `BREAKING CHANGE: <설명>` 명시 → [SemVer](https://semver.org/lang/ko/)의 MAJOR에 대응.
- 관련 이슈는 `Refs #123`, 종료는 `Closes #123` ([GitHub 키워드 문서](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)).
- AI 에이전트 커밋은 footer에 세션(도구)이 지정한 공동 작성자 줄을 붙인다. 모델명은 바뀌므로 문서에 고정하지 않는다:
  ```
  Co-Authored-By: Claude <모델명> <noreply@anthropic.com>
  ```

---

## 3. 브랜치 전략

### 3.1 명명 규칙

```
<type>/<kebab-case-요약>
```

이 저장소 실제 예: `feat/ecommerce-marketplace`, `feat/frontend-storefront`, `feat/product-search-popular`, `docs/roadmap`.

- type은 커밋 type과 동일 어휘(`feat` / `fix` / `docs` / `refactor` …).
- 요약은 **하나의 기능/기획 단위**로 좁게 잡는다. `docs/planning/`의 기능 slug를 그대로 쓰면 추적이 쉽다. 예: `feat/product-reviews`, `feat/delivery-slot`.

### 3.2 브랜치 = 기능 단위, PR 하나로 머지되는 크기

- 브랜치 하나는 **리뷰 가능한 하나의 기능**이어야 한다. 리뷰어가 하루 안에 볼 수 없을 만큼 커지면 기능을 더 쪼갠다.
- `main`은 항상 배포 가능 상태 유지. 직접 커밋 금지, PR로만 반영.
- 작업은 항상 `main`에서 최신을 받아 분기한다.

### 3.3 최신화: merge vs rebase

- **공유(push된) 브랜치**의 이미 공개된 커밋은 rebase하지 않는다(협업자 히스토리 깨짐).
- 로컬 전용 브랜치를 정리할 때만 rebase 사용.
- 참고: [Pro Git 3.6 Rebasing — "The Perils of Rebasing"](https://git-scm.com/book/en/v2/Git-Branching-Rebasing#_rebase_peril)

---

## 4. Pull Request

### 4.1 PR 크기와 단위

- **1 PR = 1 기능(브랜치)**. 서로 무관한 변경을 한 PR에 섞지 않는다.
- 커밋 이력이 깔끔하면 리뷰어가 커밋 단위로 따라 읽을 수 있다(§1 원자적 커밋의 배당금).

### 4.2 PR 제목/본문

제목은 대표 커밋과 동일한 Conventional Commits 형식을 쓴다: `feat(review): 상품 포토리뷰`.

본문 권장 템플릿:

```markdown
## 무엇을 (What)
- 이 PR이 추가/변경하는 것 요약

## 왜 (Why)
- 배경, 관련 기획 문서 링크 (docs/planning/xxx.md)

## 어떻게 (How) — 리뷰 포인트
- 비직관적 결정·트레이드오프·동시성/트랜잭션 처리 설명
- (일반 지식을 벗어난 부분은 공식 문서 링크)

## 테스트
- 추가한 테스트 / 수동 검증 방법 / 재현 절차

## 영향 범위 & 롤백
- 마이그레이션 유무, 되돌리는 법(revert 안전한지)

## 연관 PR / 이슈
- 연관된 PR을 `#111` 형태로 링크 (선행/후속·의존 관계 명시)

Closes #<issue>
```

### 4.3 연관 PR 링크 (반드시)

**연관된 PR이 존재하면 이 PR 본문에서 그 PR을 `#111` 형태로 반드시 명시(링크)한다.** 리뷰어·추적자가 변경 사슬을 따라갈 수 있어야 하기 때문이다. 아래 관계를 구분해 적는다:

- **의존(선행)**: 이 PR이 먼저 머지되어야 하는 PR에 얹혀 있음 → `Depends on #111`
- **후속**: 이 PR을 잇는 다음 작업 → `Follow-up: #112`
- **분할**: 하나의 큰 기능을 여러 PR로 쪼갬 → `Part of #110 (2/3)`
- **관련**: 위 관계는 아니지만 함께 보면 좋은 PR → `Related: #111`
- **되돌림/재작업**: `Reverts #111`, `Supersedes #111`

> **왜 (일반 지식을 벗어난 부분)**
> GitHub에서 본문에 `#111`을 쓰면 자동으로 해당 PR로 링크되고, 대상 PR 타임라인에도 역참조가 남아 **양방향 추적**이 된다. 단, `Closes/Fixes` 키워드는 **이슈에만** 자동 종료로 동작하며 PR에는 종료 효과가 없다 — PR 간 관계는 위처럼 서술형 키워드로 적는다.
> 참고: [GitHub — autolinked references](https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/autolinked-references-and-urls), [linking a PR to an issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/using-issues/linking-a-pull-request-to-an-issue)

### 4.4 머지 방식

- 기본 **Squash merge 지양, 의미 있는 커밋은 보존**한다 — §1의 원자적 커밋을 살려야 나중에 `git bisect`/`revert`가 커밋 단위로 유효하기 때문. (단순 WIP만 있는 브랜치는 정리 후 머지)
- 참고: [GitHub 머지 방식 비교](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/incorporating-changes-from-a-pull-request/about-pull-request-merges)

### 4.5 gh CLI

이 환경에서는 `gh`로 PR을 만든다:

```bash
gh pr create --base main --title "feat(review): 상품 포토리뷰" --body-file <파일>
```

PR 본문 말미(에이전트 생성 시):

```
🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

---

## 5. "일반 지식을 벗어난 내용"의 문서화 위치 (요청 원칙 #3)

한눈에 이해하기 어려운 지식은 성격에 따라 **가장 오래 살아남는 위치**에 남긴다:

| 성격 | 남기는 곳 |
|---|---|
| 이 변경에 국한된 "왜" | **커밋 본문** |
| 이 PR 전체의 리뷰 맥락 | **PR 본문 How 섹션** |
| 코드를 읽을 때 계속 필요한 주의점 | **코드 주석**(짧게) |
| 도메인/아키텍처 차원의 결정 | `docs/SERVER_ARCHITECTURE.md`, `docs/planning/*` |

**규칙**: 커밋/PR에서 비직관적 결정을 언급할 때는 가능하면 **1차 출처(공식 문서·RFC·스펙)** 링크를 붙인다. Stack Overflow보다 공식 문서를 우선한다.

자주 쓰는 1차 출처:

- Conventional Commits — https://www.conventionalcommits.org/ko/v1.0.0/
- Semantic Versioning — https://semver.org/lang/ko/
- Pro Git (한국어) — https://git-scm.com/book/ko/v2
- git bisect — https://git-scm.com/docs/git-bisect
- Spring Framework Docs — https://docs.spring.io/spring-framework/reference/
- Spring Security — https://docs.spring.io/spring-security/reference/
- PostgreSQL 트랜잭션 격리 — https://www.postgresql.org/docs/current/transaction-iso.html
- Toss Payments API — https://docs.tosspayments.com/reference

---

## 6. 요약 체크리스트

커밋 전:
- [ ] 이 커밋만 revert해도 저장소가 깨지지 않는가? (atomic)
- [ ] scope 하나에 집중되어 있는가? (기능 단위)
- [ ] 포맷팅/오타를 로직과 섞지 않았는가?
- [ ] 비직관적 결정이면 본문에 "왜" + 공식 문서 링크를 남겼는가?

PR 전:
- [ ] 브랜치명이 `type/기능-slug`인가?
- [ ] 하나의 기능만 담고 있는가?
- [ ] 본문에 What/Why/How/테스트/롤백을 적었는가?
- [ ] 관련 기획 문서(`docs/planning/*`)를 링크했는가?
- [ ] **연관 PR이 있으면 `#번호`로 링크하고 관계(의존/후속/분할/관련)를 적었는가?**
