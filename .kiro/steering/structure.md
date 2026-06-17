---
inclusion: always
---

# 프로젝트 구조

## 멀티 모듈 Gradle (모노레포)

```
ai-test-forge/
├── packages/
│   ├── server/                          # Spring Boot 메인 서버 (FE 통합 빌드 포함)
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── java/com/aitestforge/
│   │       │   ├── Application.java
│   │       │   ├── config/              # 설정 (Security, Swagger, WebMvc, SpaWebConfig)
│   │       │   ├── controller/          # REST 컨트롤러
│   │       │   │   ├── auth/            # 메인 서버 인증 엔드포인트
│   │       │   │   ├── chat/            # 채팅 대화 엔드포인트
│   │       │   │   ├── recipe/          # 레시피 관리 엔드포인트
│   │       │   │   ├── settings/        # AI/Agent Loop 설정 엔드포인트
│   │       │   │   ├── spec/            # API 스펙 관리 엔드포인트
│   │       │   │   └── workspace/       # 워크스페이스 관리 엔드포인트
│   │       │   ├── service/             # 비즈니스 로직
│   │       │   │   ├── agent/           # 에이전트 루프 오케스트레이션
│   │       │   │   ├── auth/            # 메인 서버 인증
│   │       │   │   ├── chat/            # 채팅 세션, 메시지 처리
│   │       │   │   ├── recipe/          # 레시피 CRUD, 실행, 변수 치환, 스펙 검증
│   │       │   │   ├── settings/        # AI/Agent Loop 런타임 설정
│   │       │   │   ├── spec/            # API 스펙 저장, 파싱, 비동기 처리, 유지보수
│   │       │   │   └── workspace/       # 워크스페이스 관리
│   │       │   ├── infra/              # 외부 시스템 추상화
│   │       │   │   ├── ai/             # AI 서비스 인터페이스 + 구현체
│   │       │   │   │   ├── AiService.java          # 인터페이스
│   │       │   │   │   ├── AiRetryTemplate.java    # 재시도 로직
│   │       │   │   │   ├── OpenAiService.java      # OpenAI 구현체
│   │       │   │   │   ├── ClaudeAiService.java    # Claude 구현체
│   │       │   │   │   ├── OpenRouterService.java  # OpenRouter 구현체
│   │       │   │   │   ├── MockAiService.java      # 로컬 개발용 Mock
│   │       │   │   │   └── dto/                    # AI 관련 DTO (AiChatResponse, ChatMessage, ToolCall, ToolControl, ToolDefinition)
│   │       │   │   └── auth/            # JWT 인증 필터 + 토큰 프로바이더 + Rate Limiter
│   │       │   ├── domain/             # JPA 엔티티
│   │       │   │   ├── common/         # 공통 인터페이스 (EnumColumn)
│   │       │   │   ├── chat/           # ChatSession, ChatMessage
│   │       │   │   │   └── enums/      # SessionStatus, MessageRole
│   │       │   │   ├── spec/           # SubdomainSpec
│   │       │   │   │   └── enums/      # SpecStatus
│   │       │   │   ├── auth/           # User
│   │       │   │   │   └── enums/      # UserRole
│   │       │   │   ├── recipe/         # Recipe
│   │       │   │   │   └── enums/      # RecipeVisibility, RecipeValidationStatus
│   │       │   │   └── workspace/      # Workspace, WorkspaceMapping
│   │       │   ├── repository/         # JPA Repository
│   │       │   ├── dto/                # 요청/응답 DTO (Java record)
│   │       │   │   ├── auth/           # 인증 DTO
│   │       │   │   │   ├── request/    # LoginRequest, RegisterRequest 등
│   │       │   │   │   └── response/   # LoginResponse, UserResponse 등
│   │       │   │   ├── chat/           # 채팅 DTO
│   │       │   │   │   ├── request/    # SendMessageRequest 등
│   │       │   │   │   └── response/   # SessionResponse, MessageResponse 등
│   │       │   │   ├── recipe/         # 레시피 DTO
│   │       │   │   │   ├── request/    # CreateRecipeRequest 등
│   │       │   │   │   └── response/   # RecipeResponse 등
│   │       │   │   ├── settings/       # 설정 DTO
│   │       │   │   │   ├── request/    # UpdateSettingsRequest
│   │       │   │   │   └── response/   # SettingsResponse
│   │       │   │   ├── spec/           # 스펙 DTO
│   │       │   │   │   ├── request/    # SpecRegisterRequest
│   │       │   │   │   └── response/   # SpecResponse, SpecDetailResponse 등
│   │       │   │   └── workspace/      # 워크스페이스 DTO
│   │       │   │       ├── request/    # CreateWorkspaceRequest 등
│   │       │   │       └── response/   # WorkspaceResponse 등
│   │       │   └── common/             # 공통 유틸, 예외 처리
│   │       │       ├── exception/      # BusinessException, ErrorCode, GlobalExceptionHandler
│   │       │       └── util/           # 공통 유틸리티
│   │       └── resources/
│   │           ├── application.yml
│   │           ├── application-local.yml
│   │           ├── application-dev.yml
│   │           └── application-prod.yml
│   │
│   ├── web/                             # React 채팅 UI
│   │   ├── package.json
│   │   ├── vite.config.ts
│   │   ├── tsconfig.json
│   │   ├── public/                     # 정적 에셋 (favicon 등, 빌드 시 dist에 포함)
│   │   └── src/
│   │       ├── main.tsx
│   │       ├── App.tsx
│   │       ├── index.css
│   │       ├── components/
│   │       │   ├── ui/                 # 재사용 UI (Button, Input, Card, Alert, Badge, Spinner, Toast)
│   │       │   ├── chat/              # 채팅 전용 (ChatInputBar, MessageBubble, Onboarding, SessionSidebar, ToolCallConfirmDialog, ToolCallProgress)
│   │       │   ├── subdomain/         # 서브도메인 관련 컴포넌트 (SubdomainCard)
│   │       │   ├── recipe/            # 레시피 관련 컴포넌트 (RecipeCard)
│   │       │   └── layout/           # 레이아웃 (AppHeader, AppLayout, TabNav)
│   │       ├── pages/                 # 페이지 컴포넌트 (ChatPage, LoginPage, RecipePage, SettingsPage, SubdomainPage, SubdomainDetailPage)
│   │       ├── hooks/                 # 커스텀 hooks
│   │       │   ├── useAgentRunner.ts  # Agent Runner 상태 머신
│   │       │   ├── useAuthGuard.ts    # 서브도메인 인증 상태 관리, 401 감지
│   │       │   └── useSseConnection.ts # SSE 재연결, Last-Event-ID, 지수 백오프
│   │       ├── services/             # API 호출 레이어
│   │       │   ├── authApi.ts
│   │       │   ├── chatApi.ts
│   │       │   ├── recipeApi.ts
│   │       │   ├── settingsApi.ts
│   │       │   ├── specApi.ts
│   │       │   └── workspaceApi.ts
│   │       ├── stores/               # Zustand 상태 관리
│   │       │   ├── useAgentRunnerStore.ts
│   │       │   ├── useAuthStore.ts
│   │       │   ├── useChatStore.ts
│   │       │   └── useWorkspaceStore.ts
│   │       ├── types/                # TypeScript 타입 정의
│   │       │   ├── agentRunner.ts
│   │       │   ├── chat.ts
│   │       │   ├── recipe.ts
│   │       │   ├── spec.ts
│   │       │   └── workspace.ts
│   │       ├── constants/            # 상수 (messages.ts 등)
│   │       └── utils/               # 유틸리티 함수
│   │
│   └── client-spring/                  # Spring Boot Starter (클라이언트 라이브러리)
│       ├── build.gradle.kts
│       └── src/main/
│           ├── java/com/aitestforge/client/
│           │   ├── AiTestForgeAutoConfiguration.java
│           │   ├── AiTestForgeProperties.java
│           │   ├── SpecRegistrationService.java
│           │   └── SpecPushScheduler.java
│           └── resources/
│               └── META-INF/
│                   └── spring/
│                       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── docs/
│   ├── design/                          # 디자인 명세 (HTML/CSS/JS)
│   │   ├── shared/                     # 디자인 시스템 (토큰, 컴포넌트)
│   │   └── web/                        # 채팅 UI 페이지 디자인
│   └── test/                           # QA 테스트 체크리스트
│       ├── shared/                     # 공통 테스트 인프라
│       └── web/                        # 기능별 테스트 데이터
│
├── package.json                        # 루트 pnpm 워크스페이스 (빌드 스크립트)
├── pnpm-workspace.yaml                 # pnpm 워크스페이스 설정
├── build.gradle.kts                    # 루트 빌드
├── settings.gradle.kts                 # 모듈 설정
├── .env.example                        # 환경변수 템플릿
└── README.md
```

## 컨벤션

- **BE**: 패키지 구조 `com.aitestforge.{domain}`
- **BE**: Entity → Repository → Service → Controller 레이어드 아키텍처
- **BE**: DTO는 Java record 사용
- **BE**: Entity는 Lombok @Builder, @Getter, protected no-arg constructor
- **BE**: Service 클래스는 `{Name}Service` 접미사
- **BE**: Controller와 Service는 1:1 매핑 강제하지 않음
- **FE**: 컴포넌트 파일명 PascalCase, 유틸/hooks camelCase
- **FE**: Path alias `@/` → `src/`
- **테스트**: BE `src/test/java/`에 main 구조 미러링

## BE 레이어 흐름 및 의존성 규칙

```
Controller → Service → Infra → External
```

| 레이어 | 패키지 | 역할 | 의존 가능 대상 |
|--------|--------|------|---------------|
| Controller | `controller/` | HTTP 요청/응답 처리 | service, dto |
| Service | `service/` | 도메인 비즈니스 로직 | infra, repository, domain, dto |
| Infra | `infra/` | 외부 시스템 추상화 | 외부 API, domain |
| Repository | `repository/` | 데이터 접근 | domain |

**규칙:**
- `infra/`는 `service/`에서만 참조 (controller에서 직접 참조 금지)
- `service/`는 다른 `service/`를 참조할 수 있음 (순환 참조 주의)
- 새 외부 연동 추가 시: `infra/`에 서브패키지 생성

## BE `infra/` 패키지 규칙

| 서브패키지 | 역할 | 인터페이스 | Mock 전략 |
|-----------|------|-----------|-----------|
| `ai/` | AI 채팅 완성 | `AiService` | `@Profile("local")` MockAiService |
| `auth/` | JWT 인증 처리 + Rate Limiter | 없음 (단일 구현) | 없음 |

**규칙:**
- 외부 서비스는 인터페이스 + 구현체 + Mock 구조 유지
- Mock은 `@Profile("local")`, 실제 구현체는 `@Profile("!local")`
- `infra/`는 도메인 규칙을 모름 — 시킨 대로 외부 시스템과 통신할 뿐

## 클라이언트 라이브러리 구조

```
packages/client-spring/src/main/java/com/aitestforge/client/
├── AiTestForgeAutoConfiguration.java   # @AutoConfiguration
├── AiTestForgeProperties.java          # @ConfigurationProperties
├── SpecRegistrationService.java        # OpenAPI JSON 가져와서 메인 서버에 Push
├── SpecPushScheduler.java              # 주기적 heartbeat 전송
├── annotation/                         # API 제어 어노테이션
│   ├── TestForgeExclude.java
│   ├── TestForgeBlock.java
│   ├── TestForgeConfirm.java
│   ├── TestForgeReadOnly.java
│   ├── TestForgeHint.java
│   ├── TestForgeGroup.java
│   └── TestForgeGroups.java
└── openapi/                            # springdoc 확장
    └── TestForgeOperationCustomizer.java
```

**설계 원칙:**
- Swagger 라이브러리에 직접 의존하지 않음 (HTTP로 raw JSON 획득)
- 프로필 기반 활성화 (dev/qa에서만 동작, prod 비활성화)
- `application.yml` 프로퍼티로 설정 제어
- Spring Boot Starter 자동 설정 메커니즘 활용
