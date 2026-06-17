---
inclusion: always
---

# Technology Stack

## Web UI

### 핵심 의존성
- **React 19** — 함수형 컴포넌트, hooks 기반
- **Vite 6** — 빌드 도구, HMR, 프록시 설정
- **TypeScript 5.8** — strict 모드 활성화
- **Tailwind CSS 4** — @tailwindcss/vite 플러그인 방식
- **react-router-dom 7** — SPA 라우팅
- **@tanstack/react-query 5** — 서버 상태 관리
- **zustand 5** — 전역 상태 관리 (채팅 세션, 인증)

### TypeScript 설정
- target: ES2020, moduleResolution: bundler
- strict: true, noUnusedLocals, noUnusedParameters
- path alias: `@/*` → `src/*`

### Vite 설정
- 개발 서버: port 5173
- base path: `/`
- API 프록시: `/api` → `http://localhost:8080` (changeOrigin: true)
- 빌드 출력: `dist/`

### 실시간 통신 패턴

```
[BE → FE] SSE (Server-Sent Events)
  - AI 텍스트 스트리밍
  - tool_call 실행 지시 (어떤 API를 호출할지)
  - 완료/에러 알림

[FE → BE] REST POST
  - 메시지 전송: POST /api/v1/chat/{sessionId}/messages
  - tool call 결과 전달: POST /api/v1/chat/{sessionId}/tool-result

[FE → 서브도메인] 직접 HTTP 호출 (브라우저)
  - 쿠키/세션 자동 부착
  - CORS 필요
```

## Backend (Main Server)

### 핵심 의존성
- **Java 25** (LTS)
- **Spring Boot 4.0.x** (Spring Framework 7.x)
- **Spring Data JPA** — MySQL 연동
- **Spring Boot Validation** — 요청 검증 (@Valid)
- **Spring Security** — 메인 서버 인증/인가
- **MySQL Connector/J** — DB 드라이버
- **Lombok** — 보일러플레이트 제거
- **springdoc-openapi 3.0.x** — Swagger UI + OpenAPI 3 문서 자동 생성
- **jjwt 0.12.x** — JWT 토큰 생성/검증
- **totp (dev.samstevens.totp)** — OTP 2단계 인증
- **json-path** — JSONPath 기반 데이터 추출 (레시피 extract)
- **Spring Boot Actuator + Micrometer Prometheus** — 모니터링/메트릭

### Gradle 설정
- Gradle Kotlin DSL
- Multi-module: root → packages/server, packages/client-spring
- Spring dependency management plugin

### 데이터베이스
- MySQL 8.x
- JPA ddl-auto: update (개발), validate (프로덕션)
- Hibernate MySQLDialect

### AI 연동
- OpenAI API (Chat Completions with function calling)
- Anthropic Claude API (Messages API with tool use)
- OpenRouter API (OpenAI 호환, 다양한 모델 접근)
- RestClient (Spring 7.x) 기반 HTTP 호출
- Timeout: connectTimeout 5초, readTimeout 60초
- Retry: exponential backoff (429/5xx → 최대 3회, 초기 대기 1초, 배율 2.0)
- Virtual Threads 활성화 (spring.threads.virtual.enabled=true) — 별도 스레드 풀 관리 불필요

### Profile 전략 (2개)

| Profile | 역할 | 차이점 |
|---------|------|--------|
| local (기본) | 개발 | ddl-auto: update, show-sql: true, Swagger 활성화 |
| prod | 운영 | ddl-auto: validate, Swagger 비활성화 |

- AI provider, DB URL, API key 등 나머지 설정은 전부 `.env` 환경변수로 제어
- Mock AI 여부도 `.env`의 `AI_REASONING_PROVIDER=mock` / `AI_FAST_PROVIDER=mock`으로 결정
- Client Library 활성화도 `.env`의 `AI_TEST_FORGE_ENABLED=true/false`로 제어
- `APP_PROFILE` 환경변수로 전환 (기본값: local)

### 모델 티어 (Model Tier)

작업 난이도에 따라 AI 모델을 분리하여 비용을 최적화한다.

| 티어 | Qualifier | 용도 | 기본 모델 |
|------|-----------|------|-----------|
| reasoning | `@Qualifier("reasoning")` | Agent Loop 메인 대화 (의도 파악 + tool 선택 + 오케스트레이션) | gpt-4o / claude-sonnet-4 |
| fast | `@Qualifier("fast")` | 2-Stage 필터, 레시피 Body 생성, "다음 액션" 힌트 | gpt-4o-mini / claude-haiku |

### Provider 기반 AI 서비스 분기

| Provider 설정값 | 구현체 | 설명 |
|----------------|--------|------|
| `mock` | MockAiService | 실제 호출 없음, 시뮬레이션 응답 |
| `openai` | OpenAiService | OpenAI API 호출 |
| `claude` | ClaudeAiService | Claude API 호출 |
| `openrouter` | OpenRouterService | OpenRouter API 호출 |

### 티어별 AiService 빈 등록

- `@Qualifier("reasoning")` — AgentLoopService에 주입
- `@Qualifier("fast")` — TwoStageFilterService, AiBodyGenerator에 주입
- AiServiceConfig에서 `ai.reasoning.provider` / `ai.fast.provider` 값 기반으로 구현체 선택
- `provider=mock`이면 MockAiService 등록 (프로필과 무관)

### Fallback 정책
- Body 생성(fast) 실패 → reasoning으로 1회 재시도
- 2-Stage 필터 실패 → 전체 tool 제공 (재시도 안 함)
- 힌트 생성 실패 → 힌트 없이 진행

### 런타임 설정 변경
- AI 모델(reasoning/fast 모두): 런타임 변경 불가 — `.env` 환경변수로만 변경 후 재시작
- "다음 액션" 힌트 토글: 런타임 변경 가능 (SettingsService)
- Agent Loop 파라미터: 런타임 변경 가능 (SettingsService)

### 토큰 제한
- `max-input-tokens`: 입력 토큰 상한 (초과 시 히스토리 truncate 또는 2-Stage 강제 발동)
- `max-output-tokens`: API 호출 시 max_tokens 파라미터로 전달
- 토큰 카운팅 v1: chars/3 기반 추정치 (정밀 카운팅은 향후)

### 설정 파일 구조
- `application.yml` — 공통 설정 (`.env` 환경변수 참조)
- `application-local.yml` — ddl-auto: update, show-sql: true, Swagger 활성화
- `application-prod.yml` — ddl-auto: validate, Swagger 비활성화

## Client Library (Spring Boot Starter)

### 핵심 의존성
- **Spring Boot 4.0.x BOM** (의존성 버전 관리)
- **Spring Web** (RestClient for HTTP calls)
- **spring-boot-configuration-processor** (IDE 지원)
- **Java 17 타겟 빌드** — Java 17, 21, 25, 27 등 17 이상 모든 버전에서 사용 가능

### 설계 원칙
- Swagger 라이브러리에 직접 의존하지 않음 (HTTP로 docs URL 호출)
- Spring Boot auto-configuration 메커니즘 사용
- `enabled` 프로퍼티 기반 활성화 제어

### 설정 예시 (서브도메인 서버의 application.yml)
```yaml
ai-test-forge:
  enabled: true
  server-url: http://localhost:8080
  subdomain-name: user-service
  docs-url: /v3/api-docs
```

## Known Gotchas
- Tailwind CSS 4는 `@import "tailwindcss"` 방식 (기존 @tailwind 지시어 사용 안 함)
- Vite 6에서 proxy 설정 시 `changeOrigin: true` 필수
- Spring Boot 4.x에서 Jakarta EE 11 네임스페이스 (`jakarta.` prefix)
- Spring Boot 4.x에서 Jackson 3이 기본으로 예정되어 있으나, 현재(4.0.6)는 Jackson 2 호환 유지 중 — `com.fasterxml.jackson.databind.ObjectMapper` 사용
- OpenAI function calling 응답의 `tool_calls` 배열 파싱 주의
- Claude tool use는 응답 구조가 OpenAI와 다름 → 각 구현체에서 개별 파싱
- Virtual Threads 사용 시 synchronized 블록 내 blocking I/O 주의 (pinning 이슈)

## 의존성 선택 근거 (ADR)

### Java 25 (Server)
- Java 25는 2025-09 출시 LTS. Virtual Threads, Record Patterns, Sealed Classes 등 최신 기능 활용.
- Client Library는 Java 17 타겟 유지 — fork하는 회사의 서브도메인 서버 호환성 보장.

### Spring Boot 4.0.x
- Boot 3.4는 2025-12-31 EOL. Boot 3.5는 2026-06-30 EOS 예정.
- Boot 4.0은 2025-11-20 GA, Spring Framework 7.0 기반.
- Jackson 3 기본, Jakarta EE 11, JSpecify null-safety.
- Virtual Threads 네이티브 지원 (`spring.threads.virtual.enabled=true`).

### Virtual Threads (ThreadPoolTaskExecutor 대체)
- Boot 4 + Java 25에서는 `spring.threads.virtual.enabled=true` 한 줄로 모든 요청 + @Async 메서드가 Virtual Threads로 실행.
- 기존 ThreadPoolTaskExecutor 수동 설정 불필요 → AsyncConfig에서 스레드 풀 제거.
- 주의: synchronized 블록 내 blocking I/O 시 carrier thread pinning 발생.

### Gradle 9.5.1
- Boot 4.0 Gradle 플러그인 요구사항: Gradle 8.14 이상.
- 9.x 호환성 검증 완료하여 최신 9.5.1로 업그레이드.

### springdoc-openapi 3.0.3
- 2.x는 Boot 3.x 전용, 3.0.x가 Boot 4 호환.
- Swagger UI + OpenAPI 3.1 지원.

### React ^19.1.0 (^19.0.0 대신)
- React 19.0.0에 RCE 취약점 발견 (CVE). 19.0.1, 19.1.2, 19.2.1에서 패치됨.
- 최소 19.1.0 이상으로 pinning.

### react-router-dom v7 (TanStack Router 대신)
- 오픈소스 프로젝트 특성상 기여 장벽 최소화.
- react-router-dom은 생태계에서 가장 널리 알려진 라우터.
- TanStack Router의 type-safe 이점은 있으나 학습 곡선 고려.

### MySQL 유지 (PostgreSQL 대신)
- 이 프로젝트는 여러 회사가 fork해서 사용하는 오픈소스.
- MySQL 사용 비율이 여전히 높고, fork 시 호환성 고려.
- JPA 추상화 덕분에 향후 PostgreSQL 전환 비용 낮음.

### client-spring 빌드 주의사항
- Spring Boot 플러그인 미적용 (라이브러리이므로 bootJar 불필요).
- `io.spring.dependency-management` 플러그인만으로는 BOM이 자동 적용 안 됨.
- 반드시 `dependencyManagement { imports { mavenBom("...spring-boot-dependencies:4.0.6") } }` 명시 필요.

```
