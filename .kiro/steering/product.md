---
inclusion: always
---

# 제품 개요

## 서비스 설명
AI 기반 테스트 데이터 생성 플랫폼.
여러 서브도메인 프로젝트(마이크로서비스)의 테스트 데이터를 자연어 채팅으로 생성하는 도구.
오픈소스로 공개하여 여러 회사가 fork해서 자기 환경에 맞게 사용.

## 핵심 가치

1. **서비스 간 의존성을 AI가 알아서 해결** — "입사지원 데이터 만들어줘" → 회원 생성 → 이력서 생성 → 지원 API 호출을 AI가 순서대로 처리
2. **반복적인 테스트 세팅 시간 절감** — Postman으로 10개 API 순서대로 호출하던 걸 한 문장으로 대체
3. **자연어로 표현 가능한 테스트 시나리오** — API 스펙을 몰라도 "이런 데이터 만들어줘"로 충분

## 대상 사용자
- 여러 마이크로서비스에 걸쳐 테스트 데이터가 필요한 백엔드 개발자
- 테스트 시나리오를 구성하는 QA 엔지니어
- Swagger/OpenAPI 문서를 사용하는 마이크로서비스 아키텍처 팀
- **사내 팀 단위 사용 (멀티 유저)** — 유저별 워크스페이스, 인증, 세션 분리

> **운영 활용**: 이 도구에서 검증된 특정 기능(예: 자연어 → API 호출, 크로스 서비스 오케스트레이션)을 떼어내 운영 환경의 자동화 도구로 활용할 수도 있습니다.

## 언어 정책
- **UI 기본 제공 언어**: 한국어 — 텍스트는 상수/파일로 분리하여 fork 시 교체 용이
- **AI 응답 언어**: 한국어 (설정으로 변경 가능, 프롬프트 레벨에서 제어)
- 코드(클래스명, 변수명, 메서드명, API 경로): 영어
- 에이전트 응답(개발자 소통): 한국어
- 코드 주석: 한국어 허용
- i18n 프레임워크: 현재 미도입. 텍스트를 상수로 분리해두면 향후 도입 시 마이그레이션 최소화

## 핵심 기능
1. **채팅 기반 테스트 데이터 생성** — 자연어 → 멀티 API 오케스트레이션
2. **AI 에이전트 루프** — 작업 완료까지 반복적 API 호출 (AI 판단은 BE, 실행은 FE)
3. **서브도메인 API 등록** — 클라이언트 라이브러리를 통한 OpenAPI 스펙 자동 Push + heartbeat 기반 자동 관리
4. **FE 직접 호출** — 브라우저가 서브도메인 API를 직접 호출 (쿠키/세션 자동 부착, credential 저장 불필요)
5. **워크스페이스** — 유저별로 작업할 서브도메인+환경 세트를 구성 (서버 30+ 환경 대응)
6. **서브도메인 인증** — 브라우저 로그인 기반 (SSO 자동 지원, 로그인 페이지 링크 제공)
7. **2단계 전략** — 의도 파악 → 관련 API 필터링 (API 수가 많을 때). 그룹 기반 필터링 + fallback 지원
8. **교체 가능한 AI 모듈** — 인터페이스 기반, 설정으로 OpenAI/Claude/기타 교체. 모델 티어(reasoning/fast) 분리로 비용 최적화
9. **레시피** — API 호출 패턴 저장/재사용. Direct 모드(AI 비호출, 토큰 0) + AI-Assisted 모드(body 생성만 AI, 토큰 80% 절감). 팀 공유, 자동 제안, 실행 전 스펙 검증 포함
10. **API 제어 어노테이션** — 서브도메인 개발자가 코드 레벨에서 API의 테스트 도구 동작 제어 (@TestForgeExclude, @TestForgeBlock, @TestForgeConfirm, @TestForgeReadOnly, @TestForgeHint, @TestForgeGroup)
11. **글로벌 API 제외 규칙** — 관리자가 메인 서버 설정으로 위험 API를 일괄 차단 (메서드/경로/태그 패턴)

## UI 구조

탭 기반 네비게이션. 조작은 채팅 중심, 나머지 탭은 조회/탐색용.

| 탭 | 역할 | 설명 |
|----|------|------|
| 💬 채팅 | 메인 | 자연어로 테스트 데이터 생성, 레시피 실행, 설정 변경 |
| 📡 서브도메인 | 조회 | 등록된 서버 목록/상태, 환경별 필터, API 탐색 |
| 📋 레시피 | 조회 | 저장된 레시피 목록, 카테고리/검색, 실행 버튼(→ 채팅으로 전환) |
| ⚙️ 설정 | 설정 | AI 프로바이더, 서브도메인 인증 설정, Agent Loop 파라미터 |

### 설계 원칙
- 채팅은 여전히 메인 — 모든 "실행"은 채팅에서 일어남
- 나머지 탭은 "인지 + 탐색" — 뭐가 있는지 보고, 클릭해서 채팅으로 넘기는 구조
- 대규모 환경 대응 — 서버 15+, 레시피 100+에서도 검색/필터로 탐색 가능
- 워크스페이스 — 상단에 워크스페이스 드롭다운으로 현재 작업 환경 세트 선택
- 신입 친화 — 온보딩, 퀵 액션, 서버 설명 자동 생성, 에러 해석으로 처음 쓰는 사람도 바로 사용

## 기술 스택

| 구분 | 기술 |
|------|------|
| 메인 서버 | Java 25 + Spring Boot 4.0.x + Gradle (Kotlin DSL) |
| 웹 UI | React 19 + Vite + TypeScript + Tailwind CSS 4 |
| 클라이언트 라이브러리 | Java (Spring Boot Starter) |
| 데이터베이스 | MySQL 8.x |
| AI | 인터페이스 기반 (OpenAI, Claude 구현체) |
| API 스펙 | OpenAPI 3.x JSON |

## 프로젝트 구조

```
ai-test-forge/
├── packages/
│   ├── server/              # Spring Boot 메인 서버 (FE 통합 빌드 포함)
│   │   ├── src/main/java/
│   │   ├── src/main/resources/
│   │   └── build.gradle.kts
│   ├── web/                 # React 채팅 UI
│   │   ├── src/
│   │   ├── public/          # 정적 에셋 (favicon 등, 빌드 시 dist에 포함)
│   │   ├── package.json
│   │   └── vite.config.ts
│   └── client-spring/       # Spring Boot Starter (클라이언트 라이브러리)
│       ├── src/main/java/
│       └── build.gradle.kts
├── docs/
│   ├── design/              # 페이지별 디자인 명세
│   └── test/                # QA 테스트 체크리스트
├── .kiro/
│   ├── agents/
│   ├── hooks/
│   ├── specs/
│   └── steering/
├── package.json             # 루트 pnpm 워크스페이스 (빌드 스크립트)
├── pnpm-workspace.yaml      # pnpm 워크스페이스 설정
├── .env.example
├── build.gradle.kts         # 루트 빌드 (멀티 모듈)
├── settings.gradle.kts
└── README.md
```

## 환경변수
- 모든 시크릿은 `.env` 파일에 관리 (`.env`는 gitignore 대상)
- `.env.example`에 변수명 템플릿 유지
- 서버: application.yml에서 `${ENV_VAR:default}` 형태로 참조
- 서버: `APP_PROFILE` 환경변수로 프로필 전환 (local / dev / prod, 기본값: local)
- 웹: Vite 환경변수는 `VITE_` 접두사 사용

## 빌드 & 실행

```bash
# 서버 (루트 디렉토리에서 실행)
./gradlew :packages:server:bootRun        # 실행 (FE 빌드 포함)
./gradlew :packages:server:bootJar        # JAR 빌드 (FE 빌드 → static 복사 → 단일 JAR)
./gradlew :packages:server:test           # 테스트

# 서버 (FE 빌드 스킵 — BE만 빠르게 컴파일)
./gradlew :packages:server:compileJava -x buildFrontend -x copyWeb

# 웹 UI (개발 시 — Vite HMR + API 프록시)
pnpm dev:web                               # 또는 cd packages/web && pnpm dev

# 웹 UI (수동 빌드)
pnpm build:web                             # 또는 cd packages/web && pnpm build

# 통합 빌드 (FE+BE 단일 JAR)
./gradlew :packages:server:bootJar        # FE 빌드 → static 복사 → JAR (processResources에서 자동)

# Docker (단일 컨테이너에서 FE+BE 서빙)
docker compose up -d --build               # 8080 포트에서 모든 것 서빙

# 클라이언트 라이브러리
./gradlew :packages:client-spring:build        # 빌드 + 테스트
./gradlew :packages:client-spring:publishToMavenLocal  # 로컬 퍼블리시
```

## URL 구조

| 경로 | 대상 | 설명 |
|------|------|------|
| `/` | 웹 UI (SPA) | 채팅 인터페이스 (메인) |
| `/login` | 웹 UI (SPA) | 메인 서버 로그인 페이지 |
| `/subdomains` | 웹 UI (SPA) | 서브도메인 목록/상태 탭 |
| `/subdomains/:name` | 웹 UI (SPA) | 서브도메인 상세 (API 목록) |
| `/recipes` | 웹 UI (SPA) | 레시피 목록/검색 탭 |
| `/settings` | 웹 UI (SPA) | 설정 탭 |
| `/api/v1/**` | 서버 API | 메인 서버 REST API |
| `/api/v1/chat/**` | 서버 API | 채팅/대화 엔드포인트 |
| `/api/v1/specs/**` | 서버 API | API 스펙 관리 (등록, 조회) |
| `/api/v1/auth/**` | 서버 API | 메인 서버 인증 (로그인, 회원가입, OTP) |
| `/api/v1/recipes/**` | 서버 API | 레시피 CRUD + 실행 |
| `/api/v1/workspaces/**` | 서버 API | 워크스페이스 CRUD |
| `/api/v1/settings` | 서버 API | AI/Agent Loop 런타임 설정 |
| `/health` | 서버 | 헬스 체크 |

## API 설계
- REST API 경로: `/api/v1/{resource}`
- 헬스 체크: `/health`
- 웹 UI → 서버 프록시: Vite dev server에서 `/api` → `localhost:8080`

## 로컬 개발 환경
- local 프로필: Mock AI 서비스 (실제 API 호출 없이 시뮬레이션 응답 반환)
- 웹 개발 모드: Vite dev server(5173) + API 프록시 → localhost:8080 (HMR 활용)
- 통합 빌드 모드: `./gradlew bootJar` 실행 시 FE 빌드 → static 복사 → 단일 JAR 생성
- 배포: Docker 단일 컨테이너 (8080 포트에서 FE+BE 모두 서빙)

## 배포 구조
- **단일 JAR 배포**: Spring Boot JAR에 React 빌드 결과물이 static resource로 포함
- **SPA 라우팅**: `SpaWebConfig`가 `/api`, `/health` 외 요청을 `index.html`로 포워딩
- **정적 에셋**: `packages/web/public/`에 favicon 등 → Vite 빌드 시 `dist/`에 자동 포함
- **Docker**: 멀티스테이지 빌드 (Node.js → FE 빌드, JDK → BE 빌드, JRE → 실행)

## 참조 파일
#[[file:.env.example]]
