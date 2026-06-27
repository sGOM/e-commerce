# Spring Starter Kit

Kotlin + Spring Boot 기반 스타터 킷. 세션 로그인 / OAuth2 소셜 로그인 / RBAC 권한 / 공통 응답·에러 / 감사 로그 / 관리자 기능을 확장성 있게 제공하는 것을 목표로 한다.

## 기술 스택
- **언어/빌드**: Kotlin, Gradle(Kotlin DSL), JVM 21 toolchain
- **프레임워크**: Spring Boot 3.5.x (Web MVC, Security, Data JPA, OAuth2 Client)
- **쿼리**: [Kotlin JDSL](https://github.com/line/kotlin-jdsl) — 타입 안전한 동적 쿼리
- **DB**: PostgreSQL (로컬/운영), Flyway 마이그레이션
- **테스트**: JUnit5, MockK, SpringMockK, Testcontainers, spring-security-test

## 구현 현황
| 단계 | 내용 | 상태 |
|------|------|------|
| Phase 1 | 스캐폴딩, 공통 응답/에러, BaseEntity, 프로파일/Docker | ✅ |
| Phase 2 | 도메인 모델, RBAC, 자체 회원가입/로그인, 세션 보안 | ✅ |
| Phase 3 | OAuth2 소셜 로그인(구글/네이버/카카오) + 자체계정 연동 | ✅ |
| Phase 4 | 요청별 감사 로그(JSONB) | ✅ |
| Phase 5 | 관리자 기능(사용자/권한/감사로그 검색) | ✅ |
| Phase 6 | 테스트 보강 | ⏳ |

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
    ├── user/      User·Role·Permission 엔티티/리포지토리
    └── auth/      회원가입·로그인·로그아웃·내정보 API
```

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
통합 테스트는 기본적으로 **Testcontainers(PostgreSQL)** 를 사용한다(실 환경 일치).

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

## 관리자 API (`ROLE_ADMIN` 전용, URL 규칙 + `@PreAuthorize` 이중 방어)
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
