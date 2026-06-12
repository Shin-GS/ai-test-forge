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
- **Java 21** (LTS)
- **Spring Boot 3.x** (Spring Framework 6.x)
- **Spring Data JPA** — MySQL 연동
- **Spring Boot Validation** — 요청 검증 (@Valid)
- **MySQL Connector/J** — DB 드라이버
- **Lombok** — 보일러플레이트 제거
- **springdoc-openapi** — Swagger UI + OpenAPI 3 문서 자동 생성

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
- RestClient (Spring 6.1+) 기반 HTTP 호출
- Timeout: connectTimeout 5초, readTimeout 60초

### Profile 전략 (3개)

| Profile | DB | AI Service | Client Library | 용도 |
|---------|-----|-----------|---------------|------|
| local | Local MySQL | MockAiService | Push to localhost | 로컬 개발 |
| dev | Dev MySQL | Real AI | Push to dev server | 통합 테스트 |
| prod | Prod MySQL | Real AI | **Disabled** | 프로덕션 |

### Profile 기반 AI 서비스 분기

| Provider | @Profile | 설정 |
|----------|----------|------|
| MockAiService | `local` | 실제 호출 없음, 시뮬레이션 응답 |
| OpenAiService | `!local` + `ai.provider=openai` | OpenAI API 호출 |
| ClaudeAiService | `!local` + `ai.provider=claude` | Claude API 호출 |

### 설정 파일 구조
- `application.yml` — 공통 설정
- `application-local.yml` — ddl-auto: update, show-sql: true, Swagger 활성화, Mock AI
- `application-dev.yml` — ddl-auto: update, Real AI
- `application-prod.yml` — ddl-auto: validate, Real AI, Swagger 비활성화

## Client Library (Spring Boot Starter)

### 핵심 의존성
- **Spring Boot 3.x** (auto-configuration)
- **Spring Web** (RestClient for HTTP calls)
- **spring-boot-configuration-processor** (IDE 지원)

### 설계 원칙
- Swagger 라이브러리에 직접 의존하지 않음 (HTTP로 docs URL 호출)
- Spring Boot auto-configuration 메커니즘 사용
- Profile 기반 활성화 제어

### 설정 예시 (서브도메인 서버의 application.yml)
```yaml
ai-test-forge:
  enabled: true
  server-url: http://localhost:8080
  subdomain-name: user-service
  docs-url: /v3/api-docs
  profiles: dev, qa
```

## Known Gotchas
- Tailwind CSS 4는 `@import "tailwindcss"` 방식 (기존 @tailwind 지시어 사용 안 함)
- Vite 6에서 proxy 설정 시 `changeOrigin: true` 필수
- Spring Boot 3.x에서 Jakarta EE 네임스페이스 (`jakarta.` prefix)
- OpenAI function calling 응답의 `tool_calls` 배열 파싱 주의
- Claude tool use는 응답 구조가 OpenAI와 다름 → 각 구현체에서 개별 파싱
