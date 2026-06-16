# Technical Design Document

## Overview

이 설계 문서는 레시피 시스템 개선, API 제어 어노테이션, Agent Loop 안정화를 위한 기술 설계를 정의한다.

### 설계 범위

| 영역 | BE (packages/server) | FE (packages/web) | Client Lib (packages/client-spring) |
|------|---------------------|-------------------|-------------------------------------|
| 레시피 실행/검증/저장 | ✅ | ✅ | — |
| API 제어 어노테이션 | ✅ (파싱) | ✅ (block/confirm 처리) | ✅ (어노테이션 정의) |
| Agent Loop 안정화 | ✅ (이벤트 버퍼링) | ✅ (Agent Runner) | — |

### 기존 코드 현황 (이미 구현됨)

- `Recipe` 엔티티 + CRUD (RecipeService)
- `RecipeExecutionService` — SSE 기반 step-by-step 실행, 변수 치환 기본 구현
- `SubdomainSpec` — OpenAPI JSON 저장, heartbeat, 상태 관리
- `SpecService` — 등록/갱신/상세조회, endpoint 파싱
- `AgentLoopService` — 에이전트 루프 기본 구현
- `AiService` 인터페이스 + 구현체 4개

## Data Models

### 2.1 Recipe 엔티티 변경

```java
@Entity
@Table(name = "RECIPE")
public class Recipe {
    // 기존 필드 유지
    private Long id;
    private Long userId;        // owner
    private String name;
    private String description;
    private List<String> tags;
    private String stepsJson;   // JSON 배열 (step 구조 확장)
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;

    // 추가 필드
    @Enumerated(EnumType.STRING)
    @Column(name = "VISIBILITY", nullable = false)
    @Builder.Default
    private RecipeVisibility visibility = RecipeVisibility.PUBLIC;

    @Enumerated(EnumType.STRING)
    @Column(name = "VALIDATION_STATUS")
    private RecipeValidationStatus validationStatus; // VALID, WARN, BROKEN

    @Column(name = "VALIDATION_MESSAGE")
    private String validationMessage;

    @Column(name = "VARIABLES_JSON", columnDefinition = "TEXT")
    private String variablesJson; // input 변수 정의 JSON
}
```

### 2.2 Step JSON 구조 (stepsJson 내부)

```json
[
  {
    "name": "회원 생성",
    "subdomain": "user-service",
    "environment": "dev",
    "method": "POST",
    "path": "/api/members",
    "bodyStrategy": "gen",
    "body": {
      "email": "{{gen:email}}",
      "name": "{{gen:koreanName}}"
    },
    "aiHint": null,
    "selectStrategy": null,
    "extract": {
      "memberId": "$.data.id"
    }
  },
  {
    "name": "이력서 생성",
    "subdomain": "resume-service",
    "environment": "dev",
    "method": "POST",
    "path": "/api/resumes",
    "bodyStrategy": "ai-generate",
    "body": {
      "memberId": "{{memberId}}"
    },
    "aiHint": "주니어 백엔드 개발자 이력서",
    "selectStrategy": null,
    "extract": {
      "resumeId": "$.data.id"
    }
  }
]
```

### 2.3 Variables JSON 구조 (variablesJson)

```json
[
  {
    "name": "positionId",
    "type": "input",
    "label": "포지션 ID",
    "required": true
  }
]
```

### 2.4 Enums

```java
public enum RecipeVisibility { PUBLIC, PRIVATE }
public enum RecipeValidationStatus { VALID, WARN, BROKEN }
public enum BodyStrategy { FIXED, GEN, AI_GENERATE, AI_FILL }
public enum SelectStrategy { AI_PICK }
```

### 2.5 SubdomainSpec — 제어 메타데이터 저장

기존 `SubdomainSpec.specJson`에서 파싱한 제어 정보를 별도 컬럼으로 관리할지, 런타임에 매번 파싱할지 결정 필요.

**결정: 런타임 파싱** — specJson이 갱신될 때마다 별도 컬럼 동기화하기 어려움. `SpecToolConverter`에서 tool 목록 생성 시 x-test-forge-* 필드를 함께 파싱.

## Architecture

레이어드 아키텍처를 유지하며, 기존 BE 구조(Controller → Service → Infra)에 새 서비스를 추가한다. FE는 Agent Runner 상태 머신을 도입하고, Client Library에 어노테이션 + springdoc 확장을 추가한다.

## Components and Interfaces

### 3.1 BE 컴포넌트 구조

```
service/recipe/
├── RecipeService.java              (기존 CRUD — visibility, clone 추가)
├── RecipeExecutionService.java     (기존 — 스펙 검증, AI-Assisted 모드, 진행 상태 추가)
├── RecipeVariableResolver.java     (NEW — 변수 치환 전담, gen:* 생성자 포함)
├── RecipeSpecValidator.java        (NEW — 실행 전 스펙 검증)
├── RecipeSuggestionService.java    (NEW — 채팅 요청과 유사한 레시피 검색)
└── RecipeSaverService.java         (NEW — tool_call 이력에서 레시피 생성)

service/spec/
├── SpecService.java                (기존)
├── SpecToolConverter.java          (기존 — x-test-forge-* 파싱 로직 추가)
├── SpecControlFilter.java          (NEW — global-exclude + annotation 기반 tool 필터링)
└── SpecAsyncProcessor.java         (기존)

service/agent/
├── AgentLoopService.java           (기존 — 2-Stage fallback 추가)
├── TwoStageFilterService.java      (기존 — group 기반 필터링 추가)
└── SseEventBufferService.java      (NEW — SSE 이벤트 버퍼링, 재연결 지원)
```

### 3.2 FE 컴포넌트 구조

```
hooks/
├── useAgentRunner.ts               (NEW — Agent Runner 상태 머신)
├── useAuthGuard.ts                 (NEW — 인증 상태 관리, 401 감지)
├── useSseConnection.ts             (NEW — SSE 재연결, Last-Event-ID, 지수 백오프)
└── useRecipeExecution.ts           (기존 확장 — 스펙 검증 결과 처리, 진행 상태)

services/
├── recipeApi.ts                    (기존 확장 — 검증, 실행, 저장 API)
└── agentApi.ts                     (기존 확장 — tool-result 일괄 전달)

stores/
├── agentRunnerStore.ts             (NEW — 실행 상태, 대기 중 tool_call, 인증 상태)
└── recipeStore.ts                  (기존 확장)
```

### 3.3 Client Library 컴포넌트

```
packages/client-spring/src/main/java/com/aitestforge/client/
├── annotation/                     (NEW — 어노테이션 패키지)
│   ├── TestForgeExclude.java
│   ├── TestForgeBlock.java
│   ├── TestForgeConfirm.java
│   ├── TestForgeReadOnly.java
│   ├── TestForgeHint.java
│   └── TestForgeGroup.java
└── openapi/                        (NEW — springdoc 확장)
    └── TestForgeOperationCustomizer.java
```

## API Design

### 4.1 레시피 API (변경/추가)

```
기존 유지:
POST   /api/v1/recipes                    — 생성
GET    /api/v1/recipes                    — 목록 (visibility 필터 추가)
GET    /api/v1/recipes/{id}               — 상세
PUT    /api/v1/recipes/{id}               — 수정
DELETE /api/v1/recipes/{id}               — 삭제
POST   /api/v1/recipes/{id}/execute       — 실행 (SSE)
POST   /api/v1/recipes/{id}/step-result   — step 결과

추가:
POST   /api/v1/recipes/{id}/validate      — 스펙 검증 (실행 전)
POST   /api/v1/recipes/{id}/clone         — 복제
POST   /api/v1/recipes/generate-from-session — 대화 이력에서 레시피 생성
GET    /api/v1/recipes/suggest?query=...  — 유사 레시피 제안
```

### 4.2 스펙 검증 응답 구조

```json
// POST /api/v1/recipes/{id}/validate
{
  "status": "BROKEN",  // VALID | WARN | BROKEN
  "issues": [
    {
      "stepIndex": 1,
      "stepName": "이력서 생성",
      "issueType": "REQUIRED_FIELD_ADDED",
      "description": "필수 필드 추가됨: phoneNumber (string)",
      "currentSpec": { "method": "POST", "path": "/api/resumes", ... }
    }
  ]
}
```

### 4.3 레시피 생성 (대화 이력 기반) 요청/응답

```json
// POST /api/v1/recipes/generate-from-session
// Request
{
  "sessionId": 123,
  "stepRange": [0, 3],  // 선택한 tool_call 범위 (0-indexed)
  "name": "입사지원 데이터 생성",
  "description": "회원 → 이력서 → 지원"
}

// Response
{
  "id": 45,
  "name": "입사지원 데이터 생성",
  ...
}
```

### 4.4 Tool 제어 메타데이터 포함된 Tool Definition

```json
// SpecToolConverter가 생성하는 ToolDefinition에 제어 정보 포함
{
  "name": "user-service_POST_/api/members",
  "description": "회원 생성 [AI Hint: memberId는 유효한 회원이어야 합니다]",
  "parametersJson": "{ ... }",
  "control": {
    "blocked": false,
    "confirm": null,
    "readonly": false,
    "groups": ["회원가입-플로우"]
  }
}
```

## Key Flows

### 5.1 레시피 실행 플로우

```
사용자 → "입사지원 레시피 실행해줘"
    │
    ▼
[FE] RecipeEngine.execute(recipeId, variables)
    │
    ▼
[BE] POST /api/v1/recipes/{id}/validate
    │
    ├─ VALID → 실행 시작
    ├─ WARN → FE에서 경고 표시 + 실행 여부 확인
    └─ BROKEN → FE에서 차단 + "AI로 수정" 옵션 제공
    │
    ▼ (VALID 또는 사용자 승인 시)
[BE] POST /api/v1/recipes/{id}/execute (SSE 연결)
    │
    ▼
[BE] RecipeExecutionService:
    for each step:
      1. RecipeVariableResolver로 body 치환
      2. bodyStrategy 확인:
         - fixed/gen → 즉시 tool_call_start 이벤트 전송
         - ai-generate/ai-fill → AiService.chat()으로 body 생성 후 전송
         - ai-pick → 조회 결과를 AI에게 전달 후 선택 결과로 진행
      3. SSE: tool_call_start 이벤트 → FE
    │
    ▼
[FE] Agent Runner: 서브도메인 API 직접 호출
    │
    ▼
[FE] POST /api/v1/recipes/{id}/step-result
    │
    ▼
[BE] extract → 변수 저장 → 다음 step 또는 done
```

### 5.2 레시피 저장 플로우 (대화 이력 기반)

```
사용자 → "방금 한 거 레시피로 저장해줘"
    │
    ▼
[BE] AI가 현재 세션의 tool_call 이력 분석
    │
    ├─ 전체 tool_call 목록을 사용자에게 표시
    ├─ "레시피에 포함할 범위를 선택해주세요"
    │
    ▼
사용자 → "1~4번까지"
    │
    ▼
[BE] RecipeSaverService:
    1. 선택된 tool_call에서 step 구조 추출
    2. 응답 → 파라미터 연결 분석 (JSONPath 매핑)
    3. 변수 타입 할당 (참조/input/auto)
    4. bodyStrategy 결정 (기본 fixed, 복잡한 body는 ai-fill 제안)
    │
    ▼
[BE] AI가 생성된 레시피를 자연어로 요약하여 사용자에게 확인 요청
    │
    ▼
사용자 승인 → POST /api/v1/recipes/generate-from-session → DB 저장
```

### 5.3 API 제어 플로우

```
[서브도메인 서버]
    @TestForgeBlock(reason = "실 결제 발생")
    @PostMapping("/api/payments/charge")
    │
    ▼ (springdoc + TestForgeOperationCustomizer)
[OpenAPI JSON]
    paths./api/payments/charge.post:
      x-test-forge-block: { "reason": "실 결제 발생" }
    │
    ▼ (Push to 메인 서버)
[메인 서버] SpecToolConverter.convertToTools(specJson)
    │
    ├─ x-test-forge-exclude: true → tool 목록에서 제거
    ├─ x-test-forge-block → tool 포함하되 description에 [BLOCKED: reason] 추가
    ├─ x-test-forge-confirm → tool의 control.confirm에 message 설정
    ├─ x-test-forge-readonly → tool의 control.readonly = true
    ├─ x-test-forge-hint → description 끝에 [AI Hint: ...] 추가
    └─ x-test-forge-group → tool의 control.groups에 배열 설정
    │
    ▼ (Global Rule 적용)
[메인 서버] SpecControlFilter:
    methods: [DELETE] → 매칭되는 tool 제거
    path-patterns: ["**/admin/**"] → 매칭되는 tool 제거
    │
    ▼
[Agent Loop] AI에게 필터링된 tool 목록 전달
    │
    ▼ (AI가 blocked API를 tool_call)
[FE] Agent Runner:
    - control.blocked=true → 호출 차단 + reason 표시 + 실패 결과 BE에 전달
    - control.confirm 존재 → 확인 팝업 → 승인/거부
```

### 5.4 SSE 재연결 플로우

```
[FE] 브라우저 새로고침
    │
    ▼
[FE] Agent Runner: localStorage에서 활성 세션 ID 확인
    │
    ├─ 없음 → 일반 채팅 화면
    └─ 있음 → SSE 재연결 시도
        │
        ▼
[FE] GET /api/v1/chat/{sessionId}/stream
     Headers: Last-Event-ID: "evt-42"
        │
        ▼
[BE] SseEventBufferService:
     - 버퍼에 evt-42 이후 이벤트 있음 → 순서대로 전송
     - tool_call 대기 중 → tool_call_start 재전송
        │
        ▼
[FE] 누락된 이벤트 반영 → 대기 중 tool_call 재실행 → 정상 진행
```

### 5.5 인증 플로우 (401 처리)

```
[FE] Agent Runner: 서브도메인 API 호출 → 401 응답
    │
    ▼
[FE] Auth Guard:
    1. 에이전트 루프 일시정지 (실행 컨텍스트 보존)
    2. 해당 서브도메인의 auth profile에서 login-page-url 조회
    3. 사용자에게 로그인 링크 표시
    │
    ▼ (사용자가 새 탭에서 로그인)
    │
    ▼
[FE] Auth Guard: 주기적 폴링 (10초 간격)
    - 해당 서브도메인의 아무 GET API 호출 시도
    - 401 아닌 응답 → 로그인 완료 판정
    │
    ▼
[FE] Agent Runner: 실패한 tool_call 재실행 → 루프 재개
    │
    ▼ (5분 초과)
[FE] Auth Guard: 타임아웃 → 에이전트 루프 종료 + 에러 메시지
```

## Configuration

### 6.1 application.yml 추가 설정

```yaml
spec-registry:
  # 기존
  stale-threshold: 5m
  auto-delete-threshold: 30m
  async-threshold: 5242880
  # 추가
  global-exclude:
    methods: []                    # 예: [DELETE]
    path-patterns: []              # 예: ["**/admin/**", "**/internal/**"]
    tags: []                       # 예: ["deprecated"]

recipe:
  max-steps: 30
  step-timeout-seconds: 60
  ai-body-max-retries: 2
  validation:
    enabled: true
    background-check-on-spec-update: true
```

### 6.2 Client Library 설정 (서브도메인의 application.yml)

```yaml
ai-test-forge:
  # 기존 설정 유지
  enabled: true
  server-url: http://localhost:8080
  subdomain-name: user-service
  docs-url: /v3/api-docs
  # 어노테이션은 별도 설정 불필요 — springdoc OperationCustomizer로 자동 반영
```

## Annotation Design (Client Library)

### 7.1 어노테이션 정의

```java
package com.aitestforge.client.annotation;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeExclude {
    String reason() default "";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeBlock {
    String reason() default "";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeConfirm {
    String message() default "";
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeReadOnly {}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeHint {
    String value();
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(TestForgeGroups.class)
public @interface TestForgeGroup {
    String value();
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestForgeGroups {
    TestForgeGroup[] value();
}
```

### 7.2 OperationCustomizer (springdoc 확장)

```java
package com.aitestforge.client.openapi;

@Component
@RequiredArgsConstructor
public class TestForgeOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        Class<?> declaringClass = method.getDeclaringClass();

        // 클래스 레벨 @TestForgeExclude
        if (declaringClass.isAnnotationPresent(TestForgeExclude.class)) {
            TestForgeExclude ann = declaringClass.getAnnotation(TestForgeExclude.class);
            addExtension(operation, "x-test-forge-exclude",
                ann.reason().isEmpty() ? true : Map.of("reason", ann.reason()));
            return operation;
        }

        // 메서드 레벨 어노테이션 처리
        if (method.isAnnotationPresent(TestForgeExclude.class)) { ... }
        if (method.isAnnotationPresent(TestForgeBlock.class)) { ... }
        if (method.isAnnotationPresent(TestForgeConfirm.class)) { ... }
        if (method.isAnnotationPresent(TestForgeReadOnly.class)) { ... }
        if (method.isAnnotationPresent(TestForgeHint.class)) { ... }

        // @TestForgeGroup (복수 가능)
        TestForgeGroup[] groups = method.getAnnotationsByType(TestForgeGroup.class);
        if (groups.length > 0) {
            List<String> groupNames = Arrays.stream(groups)
                .map(TestForgeGroup::value).toList();
            addExtension(operation, "x-test-forge-group", groupNames);
        }

        return operation;
    }
}
```

## Correctness Properties

### Property 1: Tool Control Priority

**Validates: Requirements 8, 9, 10, 11, 13**

어노테이션 충돌 시 우선순위:

```
1. Global Exclude (application.yml) → 무조건 제거, 복구 불가
2. @TestForgeExclude → AI에게 안 보임
3. @TestForgeBlock → AI에게 보이지만 실행 불가
4. @TestForgeConfirm → 실행 전 확인
5. @TestForgeReadOnly → 확인 없이 즉시 실행 (Confirm보다 우선)

Block > ReadOnly (둘 다 있으면 Block 적용)
ReadOnly > Confirm (둘 다 있으면 확인 스킵)
```

## Error Handling

### 9.1 BE: SseEventBufferService

```java
@Service
public class SseEventBufferService {

    // 세션별 이벤트 버퍼 (최대 120초 보관)
    private final Map<Long, Deque<SseBufferedEvent>> buffers = new ConcurrentHashMap<>();

    record SseBufferedEvent(String id, String type, String data, Instant createdAt) {}

    // 이벤트 발행 시 버퍼에도 저장
    public void publish(Long sessionId, String type, Object data) { ... }

    // 재연결 시 lastEventId 이후 이벤트 반환
    public List<SseBufferedEvent> getEventsAfter(Long sessionId, String lastEventId) { ... }

    // 120초 초과 이벤트 정리 (스케줄러)
    @Scheduled(fixedRate = 30_000)
    public void cleanExpiredEvents() { ... }
}
```

### 9.2 FE: SSE 재연결 로직

```typescript
// useSseConnection.ts
const reconnect = (sessionId: string, lastEventId: string) => {
  let attempt = 0;
  const maxAttempts = 3;
  const baseDelay = 1000;

  const tryConnect = () => {
    const eventSource = new EventSource(
      `/api/v1/chat/${sessionId}/stream`,
      { headers: { 'Last-Event-ID': lastEventId } }
    );
    // 성공 → attempt 리셋
    // 실패 → delay = min(baseDelay * 2^attempt, 10000)
    // 3회 실패 → 수동 재연결 버튼 표시
  };
};
```

## Implementation Priority

| 우선순위 | 작업 | 이유 |
|---------|------|------|
| P0 | Recipe 엔티티 확장 (visibility, variables, validation) | 후속 작업의 기반 |
| P0 | RecipeVariableResolver (gen:*, JSONPath extract) | 실행 엔진 핵심 |
| P0 | RecipeSpecValidator | 실행 안정성 핵심 |
| P1 | 어노테이션 정의 (client-spring) | 서브도메인 개발자 경험 |
| P1 | SpecControlFilter + x-test-forge-* 파싱 (server) | 어노테이션 동작에 필수 |
| P1 | RecipeExecutionService AI-Assisted 모드 | 핵심 기능 |
| P2 | RecipeSaverService (대화 이력 → 레시피 생성) | 생성 UX |
| P2 | RecipeSuggestionService (유사 레시피 제안) | 사용성 향상 |
| P2 | 레시피 공유/복제 (visibility, clone) | 팀 협업 |
| P3 | SseEventBufferService (이벤트 버퍼링) | 안정성 |
| P3 | FE Agent Runner 상태 머신 (재연결, 동시 tool_call) | 안정성 |
| P3 | Auth Guard (401 감지, 폴링, 재개) | 안정성 |
| P3 | 2-Stage fallback | 정확도 개선 |
| P3 | Global Exclude 설정 | 관리자 기능 |

## Testing Strategy

- **Unit**: RecipeVariableResolver (gen 타입별 생성, JSONPath 추출, 변수 참조)
- **Unit**: RecipeSpecValidator (스펙 일치/경미 변경/호환 불가 시나리오)
- **Unit**: SpecControlFilter (global-exclude 패턴 매칭, 어노테이션 필터링)
- **Integration**: RecipeExecutionService (SSE 스트림, step 순차 실행, AI-Assisted)
- **Integration**: 어노테이션 → OpenAPI JSON 변환 → 메인 서버 파싱 E2E
