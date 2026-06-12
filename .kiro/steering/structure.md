---
inclusion: always
---

# 프로젝트 구조

## 멀티 모듈 Gradle (모노레포)

```
ai-test-forge/
├── packages/
│   ├── server/                          # Spring Boot 메인 서버
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── java/com/aitestforge/
│   │       │   ├── Application.java
│   │       │   ├── config/              # 설정 (Async, Swagger, WebMvc, AI)
│   │       │   ├── controller/          # REST 컨트롤러
│   │       │   │   ├── chat/            # 채팅 대화 엔드포인트
│   │       │   │   ├── spec/            # API 스펙 관리 엔드포인트
│   │       │   │   ├── auth/            # 서브도메인 인증 엔드포인트
│   │       │   │   └── admin/           # 관리 엔드포인트
│   │       │   ├── service/             # 비즈니스 로직
│   │       │   │   ├── chat/            # 채팅 세션, 메시지 처리
│   │       │   │   ├── agent/           # 에이전트 루프 오케스트레이션
│   │       │   │   ├── spec/            # API 스펙 저장, 파싱
│   │       │   │   ├── auth/            # 서브도메인 인증
│   │       │   │   ├── recipe/          # 레시피 CRUD, 실행
│   │       │   │   └── tool/            # 툴 실행 (실제 API 호출)
│   │       │   ├── infra/              # 외부 시스템 추상화
│   │       │   │   ├── ai/             # AI 서비스 인터페이스 + 구현체
│   │       │   │   │   ├── AiService.java          # 인터페이스
│   │       │   │   │   ├── OpenAiService.java      # OpenAI 구현체
│   │       │   │   │   ├── ClaudeAiService.java    # Claude 구현체
│   │       │   │   │   └── MockAiService.java      # 로컬 개발용 Mock
│   │       │   │   └── http/            # 서브도메인 API 호출 HTTP 클라이언트
│   │       │   ├── domain/             # JPA 엔티티
│   │       │   │   ├── chat/           # ChatSession, ChatMessage
│   │       │   │   ├── spec/           # SubdomainSpec, ApiEndpoint
│   │       │   │   ├── auth/           # SubdomainAuth, AuthToken
│   │       │   │   └── recipe/         # Recipe, RecipeStep
│   │       │   ├── repository/         # JPA Repository
│   │       │   ├── dto/                # 요청/응답 DTO (Java record)
│   │       │   └── common/             # 공통 유틸, 예외 처리
│   │       │       ├── exception/      # BusinessException, ErrorCode
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
│   │   └── src/
│   │       ├── main.tsx
│   │       ├── App.tsx
│   │       ├── index.css
│   │       ├── components/
│   │       │   ├── ui/                 # 재사용 UI (Button, Input, Modal, Toast)
│   │       │   ├── chat/              # 채팅 전용 (MessageBubble, InputBar, ToolCallCard)
│   │       │   └── layout/           # 레이아웃 (Sidebar, Header)
│   │       ├── pages/                 # 페이지 컴포넌트 (Chat, Settings, SubdomainList)
│   │       ├── hooks/                 # 커스텀 hooks
│   │       ├── services/             # API 호출 레이어
│   │       ├── stores/               # Zustand 상태 관리
│   │       ├── types/                # TypeScript 타입 정의
│   │       └── constants/            # 상수
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
| `http/` | 서브도메인 API 호출 | 없음 (단일 구현) | 없음 |

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
└── SpecPushScheduler.java              # 주기적 재등록 (선택)
```

**설계 원칙:**
- Swagger 라이브러리에 직접 의존하지 않음 (HTTP로 raw JSON 획득)
- 프로필 기반 활성화 (dev/qa에서만 동작, prod 비활성화)
- `application.yml` 프로퍼티로 설정 제어
- Spring Boot Starter 자동 설정 메커니즘 활용
