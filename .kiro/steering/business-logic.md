---
inclusion: always
---

# Business Logic

## 1. 서브도메인 API 등록

- 등록 방식: **Push 전용** — 클라이언트 라이브러리 또는 수동 업로드로 메인 서버에 전달
- 등록 정보: 서브도메인 이름, 환경(environment), base URL, OpenAPI JSON (raw)
- 식별 키: `name + environment` 조합으로 유니크 (같은 서비스의 다른 환경을 구분)
- 갱신: heartbeat 기반 — 주기적으로 push (push = 등록 + 갱신 + heartbeat 겸용)
- 상태 관리: 메인 서버에서 등록 시각 + 마지막 heartbeat 시각 기록
- 자동 삭제: heartbeat 미응답 시 STALE → TTL 초과 시 자동 삭제
- Swagger 버전 호환: 메인 서버에서 범용 JSON 파서로 처리 (Swagger 2 → OpenAPI 3 변환 레이어 필요 시 추가)
- **OpenAPI JSON 제공이 기본 전제** — 언어 무관 (Java, Node.js, PHP, Python 등 어디든 OpenAPI 생성 도구 있음)

### 등록 방법 (3가지)

| 방법 | 대상 | 설명 |
|------|------|------|
| 클라이언트 라이브러리 (Push) | Spring Boot 서버 | 의존성 추가만으로 자동 등록 + heartbeat |
| API 직접 호출 (Push) | 모든 언어 | `POST /api/v1/specs/register`에 JSON 전송 (언어별 스크립트로 구현) |
| 수동 업로드 | OpenAPI URL 없는 서버 | 웹 UI에서 JSON 파일 업로드 또는 채팅으로 등록 |

### 대형 스펙 처리

API 수가 많으면 OpenAPI JSON이 수십 MB가 될 수 있음 (API당 request/response 스키마, example, error response 등 포함).

- **비동기 등록**: 메인 서버는 push 수신 시 즉시 `202 Accepted` 반환 → 백그라운드에서 파싱 + 저장
- **등록 상태**: REGISTERING → ACTIVE (파싱 완료 후 전환)
- **heartbeat 최적화**: 스펙 변경 없으면 JSON 해시만 전송 (재전송 불필요). 해시 불일치 시에만 전체 JSON 재전송.
- **일반 크기 (5MB 이하)**: 동기 처리도 가능 (설정으로 선택)

### 상태 전이

```
[push 수신] → REGISTERING (비동기 파싱 중)
[파싱 완료] → ACTIVE
[주기적 heartbeat (해시만)] → ACTIVE 유지
[heartbeat 미응답 N분] → STALE (UI에서 ⚠️ 표시, tool로는 여전히 제공)
[heartbeat 미응답 M분] → 자동 삭제 (tool에서 제외)
```

### 메인 서버 설정

```yaml
spec-registry:
  stale-threshold: 5m         # heartbeat 없으면 STALE
  auto-delete-threshold: 30m  # heartbeat 없으면 삭제 (0이면 자동 삭제 비활성화)
  async-threshold: 5mb        # 이 크기 이상이면 비동기 처리
```

### environment 자동 감지

- K8s: namespace 환경변수에서 자동 추출 (`KUBERNETES_NAMESPACE` 등)
- CI/CD: 브랜치명을 환경변수로 주입
- 미설정 시: "default"

### 클라이언트 라이브러리 동작 (Spring Boot용)

1. 서브도메인 서버 시작 → ApplicationReadyEvent 감지
2. 설정된 Swagger docs URL (예: `/v3/api-docs`) HTTP GET 호출
3. 응답 JSON을 메인 서버 `/api/v1/specs/register` 엔드포인트로 POST
4. 실패 시 재시도 (최대 3회, 5초 간격)
5. 이후 주기적으로 heartbeat 전송 (JSON 해시만, 변경 시에만 전체 재전송)
6. 설정 파일로 제어:
   - `ai-test-forge.server-url`: 메인 서버 주소
   - `ai-test-forge.enabled`: 활성화 여부 (default: true)
   - `ai-test-forge.profiles`: 활성화할 프로필 목록 (default: dev, qa)
   - `ai-test-forge.docs-url`: OpenAPI docs URL (default: /v3/api-docs)
   - `ai-test-forge.subdomain-name`: 서브도메인 식별명 (필수)
   - `ai-test-forge.environment`: 환경 식별 (선택, 미설정 시 자동 감지 또는 "default")
   - `ai-test-forge.base-url`: 메인 서버에서 이 서브도메인에 접근 가능한 URL (필수)
   - `ai-test-forge.heartbeat-interval`: heartbeat 주기 (default: 30s)

### 비-Java 서버 연동

클라이언트 라이브러리 없이도 연동 가능. `/api/v1/specs/register` API를 직접 호출하면 됨.

```bash
# 예: Node.js 서버 시작 스크립트에서
curl -X POST http://main-server:8080/api/v1/specs/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "payment-service",
    "environment": "dev",
    "baseUrl": "http://payment-service:3000",
    "specJson": "$(cat openapi.json)"
  }'
```

## 2. 채팅 세션

- 생성: 사용자가 새 대화 시작 시 세션 생성
- 상태: ACTIVE → COMPLETED
- 세션 내 메시지: 사용자 메시지, AI 응답, 툴 콜 결과 모두 저장
- 컨텍스트 관리: 세션 내 모든 메시지를 AI에게 전달 (히스토리)
- 세션 종료: 사용자가 명시적으로 종료하거나, AI가 작업 완료 판단 시

## 3. AI Agent Loop (핵심 메커니즘)

```
while (!completed) {
    // 1. AI에게 현재 대화 히스토리 + 사용 가능한 API 목록(tool) 전달
    AiResponse response = aiService.chat(conversation, availableTools);

    // 2. AI가 API 호출을 요청하면 실행
    if (response.hasToolCalls()) {
        for (ToolCall call : response.getToolCalls()) {
            ApiResult result = toolExecutor.execute(call, authTokens);
            conversation.addToolResult(call.id(), result);
        }
    }

    // 3. AI가 사용자에게 질문하거나 완료 메시지면 전달
    if (response.hasMessage()) {
        sendToUser(response.getMessage());
        completed = response.isFinished();
    }
}
```

### Loop 제약
- 최대 반복 횟수: 설정 가능 (기본 20회)
- 단일 턴 최대 tool call: 설정 가능 (기본 5개)
- 타임아웃: 전체 루프 타임아웃 (기본 120초)
- 무한 루프 방지: 동일 API 동일 파라미터로 3회 이상 실패 시 중단 + 사용자에게 보고

## 4. 2-Stage Strategy (API 수가 많을 때)

대량 API 환경에서 토큰 낭비와 혼란 방지를 위한 전략.

### 크로스 서비스 시나리오 (킬러 유스케이스)

여러 서브도메인에 걸친 데이터 생성이 이 도구의 핵심 가치.

**예시: "123번 포지션에 입사지원 데이터 만들어줘"**

```
사용자: "123번 포지션에 입사지원해줘"

AI 판단:
  1. 입사지원에는 회원이 필요 → 회원서비스 API 확인
  2. 이력서가 필요 → 이력서서비스 API 확인
  3. 지원 API 호출 → 채용서비스 API 확인

AI 실행 (Agent Loop):
  Step 1: [회원서비스] POST /api/members → 회원 생성 (ID: 456)
  Step 2: [이력서서비스] POST /api/resumes (memberId: 456) → 이력서 생성 (ID: 789)
  Step 3: [채용서비스] POST /api/applications (positionId: 123, resumeId: 789) → 지원 완료

AI 응답:
  ✅ 입사지원 완료
  - 회원: test_user@example.com (ID: 456)
  - 이력서: ID 789
  - 지원: 포지션 123에 지원 완료 (지원 ID: 1011)
```

핵심: 사용자는 "입사지원해줘"만 말했지만, AI가 서비스 간 의존 관계를 파악하고 필요한 선행 데이터를 자동 생성함.

### Stage 1: Intent Detection (의도 파악)
- 사용자 요청 + 등록된 서브도메인 목록 + 각 도메인의 카테고리/태그 정보를 AI에게 전달
- AI가 관련 도메인과 의도를 판단 (회원? 채용? 결제?)
- 출력: 관련 서브도메인 + 관련 API 카테고리

### Stage 2: Filtered Execution (필터된 실행)
- Stage 1 결과로 해당 도메인의 관련 API 10~20개만 tool로 선별 제공
- 이 필터된 도구 세트로 Agent Loop 실행

### 필터링 기준
- OpenAPI의 tags 활용
- API path prefix 기반 그룹핑
- 사전 정의된 도메인-태그 매핑 (설정 가능)

## 5. 서브도메인 인증

### 인증 설정
- 서브도메인별 로그인 방식 설정:
  - 로그인 API 경로
  - 요청 바디 형식 (어떤 필드에 ID/PW를 넣는지)
  - 토큰 추출 위치 (응답 바디의 어떤 필드, 또는 헤더)
  - 토큰 부착 방식 (Authorization: Bearer, 커스텀 헤더 등)

### 인증 플로우
1. 사용자가 웹 UI에서 서브도메인별 로그인 정보 입력
2. 메인 서버가 해당 서브도메인의 로그인 API 호출
3. 토큰 획득 → 세션에 저장
4. AI의 API 호출 시 해당 서브도메인 토큰 자동 부착

### 토큰 관리
- 세션 단위 저장 (사용자별 + 서브도메인별)
- 401 감지 시 토큰 만료로 판단 → 사용자에게 재로그인 요청
- 토큰 갱신 로직은 서브도메인마다 다를 수 있음 → 설정으로 대응

## 6. Tool Execution (API 호출 실행)

- AI가 tool call 요청 시 실제 서브도메인 API 호출
- 인증 토큰 자동 부착
- 요청 구성: HTTP method, URL, headers, body (AI가 결정)
- 응답 처리: HTTP status + response body를 AI에게 결과로 전달
- 에러 처리:
  - 4xx: 에러 메시지를 AI에게 전달 (AI가 수정 후 재시도 판단)
  - 5xx: 에러 발생 사실을 AI에게 전달 + 재시도 카운트
  - Timeout: 타임아웃 사실을 AI에게 전달
  - 401: 토큰 만료 → 사용자에게 재로그인 유도

## 7. AI 모듈 (교체 가능)

### Interface
```java
public interface AiService {
    AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools);
}
```

### Implementation Selection
- application.yml에서 `ai.provider` 설정 (openai, claude, mock)
- @Profile 또는 @ConditionalOnProperty로 빈 선택
- 회사마다 자기 선호 모델로 구현체만 교체

### Mock (Local Development)
- 실제 API 호출 없이 시뮬레이션 응답 반환
- 간단한 패턴 매칭으로 tool call 시뮬레이션
- 개발/테스트 시 AI 비용 없이 흐름 검증 가능

## 8. 프로필 기반 제어

| Profile | Client Library | AI Service | DB | Use Case |
|---------|---------------|------------|-----|----------|
| local | Push to localhost | MockAiService | Local MySQL | Local development |
| dev | Push to dev server | Real AI | Dev MySQL | Integration testing |
| prod | **Disabled** | Real AI | Prod MySQL | Production (client lib disabled) |

## 9. 보안

- 서브도메인 인증은 각 서버 자체의 로그인 API 활용 (별도 인증 서버 없음)
- 메인 서버 자체 인증: 초기에는 미구현 (로컬/사내 네트워크 사용 전제)
- 서브도메인 비밀번호: 메인 서버 DB에 암호화 저장 (AES)
- API 스펙에 민감정보 포함 여부 주의 (OpenAPI JSON에 example 값 등)

## 10. 미결정 사항

- 대화 히스토리 영구 저장 여부 (현재는 세션 단위만 보관)
- 멀티 유저 지원 범위 (현재는 단일 사용자 가정)
- Client Library 배포 방식 (Maven Central? GitHub Packages? JitPack?)

## 11. 실시간 업데이트 (SSE)

- 방식: Server-Sent Events (SSE)
- 이유: Agent Loop 실행 중 중간 상태(tool call 시작/완료, AI 텍스트 스트리밍)를 실시간으로 전달해야 함. WebSocket은 양방향이 불필요하고, Polling은 UX가 좋지 않음.
- 엔드포인트: `GET /api/v1/chat/{sessionId}/stream` (text/event-stream)
- 이벤트 타입:
  - `message`: AI 텍스트 응답 (스트리밍 청크)
  - `tool_call_start`: tool call 시작 (API명, 파라미터)
  - `tool_call_result`: tool call 완료 (결과 요약)
  - `done`: Agent Loop 완료
  - `error`: 에러 발생
- 연결: 사용자가 메시지 전송 후 SSE 연결 → Agent Loop 완료 시 연결 종료
- 재연결: 네트워크 끊김 시 EventSource 자동 재연결 (Last-Event-ID 기반)

## 12. 레시피 (Recipe)

자주 사용하는 API 호출 패턴을 저장하고 재사용하는 기능.

### 개념
- 레시피 = "입사지원 데이터 생성"처럼 반복되는 멀티 API 호출 순서를 템플릿화한 것
- 한번 대화로 만든 패턴을 저장해두고, 다음에 "이 레시피 실행해줘"로 즉시 재실행
- 파라미터화 가능 (매번 다른 값 주입)

### 레시피 구조

```yaml
name: "입사지원 데이터 생성"
description: "회원 생성 → 이력서 생성 → 포지션에 지원"
tags: [채용, 회원]
steps:
  - subdomain: user-service
    api: POST /api/members
    params: { email: "{{auto}}", name: "{{auto}}" }
    output: memberId
  - subdomain: resume-service
    api: POST /api/resumes
    params: { memberId: "{{memberId}}", title: "{{auto}}" }
    output: resumeId
  - subdomain: recruit-service
    api: POST /api/applications
    params: { positionId: "{{input:포지션ID}}", resumeId: "{{resumeId}}" }
```

변수 타입:
- `{{auto}}`: AI가 자동 생성
- `{{input:라벨}}`: 실행 시 사용자에게 물어봄
- `{{이전step_output}}`: 이전 단계 결과 참조

### 저장 위치
- 기본: 메인 서버 DB (레시피 CRUD API 제공)
- 확장: 레시피 소스 인터페이스로 외부 연동 가능

### 레시피 소스 인터페이스 (확장)

```java
public interface RecipeSource {
    List<Recipe> getAll();
    Optional<Recipe> getByName(String name);
}
```

구현체 (선택적 추가):
- `LocalRecipeSource` — DB (기본, 항상 활성)
- `JiraRecipeSource` — Jira 이슈에서 레시피 추출
- `NotionRecipeSource` — Notion 페이지에서 추출
- `FileRecipeSource` — YAML 파일에서 로드

### 사용 방식

**실행:**
```
사용자: "입사지원 레시피 실행해줘"
AI: 포지션 ID를 알려주세요. ({{input:포지션ID}} 변수)
사용자: "123"
AI: ✅ 레시피 실행 완료
    - 회원 생성 (ID: 456)
    - 이력서 생성 (ID: 789)
    - 포지션 123에 지원 완료 (ID: 1011)
```

**저장:**
```
사용자: "방금 한 작업을 레시피로 저장해줘"
AI: 레시피 이름을 뭘로 할까요?
사용자: "입사지원 데이터 생성"
AI: ✅ 레시피 저장 완료 (3단계, 변수 1개)
```

### 레시피 vs 직접 대화

| 상황 | 추천 | AI 토큰 비용 |
|------|------|-------------|
| 처음 해보는 시나리오 | 직접 대화 (AI가 의존관계 파악) | 높음 (의도 파악 + API 선별 + 실행) |
| 반복되는 시나리오 | 레시피 실행 (빠르고 일관됨) | **거의 0** (AI 비호출, 직접 실행) |
| 약간 다른 변형 | 레시피 실행 후 추가 대화로 수정 | 낮음 (추가 부분만 AI) |

### 레시피 실행 = AI 비호출

레시피 실행 시에는 AI Agent Loop를 거치지 않고, 저장된 단계를 순차적으로 직접 실행:

```
[일반 대화]   사용자 → AI 의도파악(토큰) → API 필터(토큰) → 실행루프(토큰) = 비용 큼
[레시피 실행] 사용자 → 변수 확인 → 저장된 단계 순차 실행 = 비용 거의 0
```

- 변수(`{{input:...}}`) 부분만 사용자에게 물어봄 (AI 불필요, 단순 프롬프트)
- `{{auto}}` 변수는 규칙 기반 생성 (랜덤 이메일, 이름 등 — AI 불필요)
- API 호출 순서/파라미터는 레시피에 이미 정의되어 있으므로 AI 판단 불필요
- 실패 시에만 AI에게 복구 판단 위임 (선택적)

### 미결정 사항
- 레시피 버전 관리 (API 변경 시 레시피 깨짐 대응)
- 레시피 공유 방식 (export/import, 팀 간 공유)
- 레시피 실행 실패 시 중간 롤백 여부
