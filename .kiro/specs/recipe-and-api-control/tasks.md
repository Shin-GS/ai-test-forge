# Implementation Plan:

## Overview

레시피 시스템 개선(실행/검증/저장/공유), API 제어 어노테이션(6개 + 글로벌 규칙 + 파싱), Agent Loop 안정화(SSE 버퍼링/재연결/동시 tool call/인증)를 구현한다.

## Tasks

- [x] 1. Recipe 엔티티 확장 — visibility, variablesJson, validationStatus 필드 추가 (Requirements: 1, 2, 6)
  - `Recipe` 엔티티에 `visibility`(RecipeVisibility: PUBLIC/PRIVATE), `variablesJson`(TEXT), `validationStatus`(RecipeValidationStatus: VALID/WARN/BROKEN), `validationMessage` 필드 추가
  - `RecipeVisibility`, `RecipeValidationStatus` enum 생성
  - `RecipeRepository`에 public+본인 private 조회 메서드 추가
  - `CreateRecipeRequest`, `RecipeResponse` DTO에 새 필드 반영
  - `RecipeService`의 create/getAll 메서드 수정

- [x] 2. RecipeVariableResolver 구현 — gen 생성자, JSONPath extract, 변수 참조 치환 (Requirements: 1, 3)
  - `service/recipe/RecipeVariableResolver.java` 생성
  - gen 타입별 생성: email, koreanName, phone, uuid, number, date
  - 미지원 gen 타입 에러 처리
  - `{{변수명}}` 참조 치환 (이전 step extract + input 변수)
  - 미정의 변수 참조 시 에러 반환
  - JSONPath 기반 extract (jayway JsonPath 라이브러리)
  - JSONPath 추출 실패 시 에러 + 실제 응답 body 반환
  - `RecipeExecutionService`의 기존 변수 치환 로직을 교체

- [x] 3. build.gradle.kts 의존성 추가 — JsonPath, springdoc (Requirements: 3)
  - `packages/server/build.gradle.kts`에 `com.jayway.jsonpath:json-path:2.9.0` 추가
  - `packages/client-spring/build.gradle.kts`에 springdoc-openapi compileOnly 의존성 추가

- [x] 4. RecipeSpecValidator 구현 — 실행 전 스펙 호환성 검증 (Requirements: 2)
  - `service/recipe/RecipeSpecValidator.java` 생성
  - 각 step의 method+path가 현재 SubdomainSpec에 존재하는지 확인
  - required 필드 목록 추출 및 레시피 body와 비교
  - 검증 결과 DTO: `RecipeValidationResult`, `ValidationIssue`
  - `RecipeController`에 `POST /api/v1/recipes/{id}/validate` 엔드포인트 추가
  - 실행 전 자동 validate 호출
  - 스펙 갱신 시 영향받는 레시피 백그라운드 검증

- [x] 5. RecipeExecutionService AI-Assisted 모드 구현 (Requirements: 1)
  - `BodyStrategy` enum 생성 (FIXED, GEN, AI_GENERATE, AI_FILL)
  - step JSON에서 bodyStrategy 파싱
  - AI_GENERATE: API 스키마 추출 → AiService.chat() → body 생성
  - AI_FILL: 빈 필드만 AI에게 채우기 요청
  - AI_PICK (selectStrategy): GET 결과 + ai-hint → AI 선택
  - AI body 스키마 검증 + 최대 2회 재시도
  - 실행 진행 상태 SSE 이벤트 (step_progress)

- [x] 6. 어노테이션 정의 — client-spring 패키지 (Requirements: 8, 9, 10, 11, 12)
  - `annotation/` 패키지에 6개 어노테이션 생성: TestForgeExclude, Block, Confirm, ReadOnly, Hint, Group
  - `@TestForgeGroup`은 @Repeatable 지원
  - `openapi/TestForgeOperationCustomizer` 구현 — 어노테이션 → x-test-forge-* 변환
  - 클래스 레벨 @TestForgeExclude 처리
  - `AiTestForgeAutoConfiguration`에 조건부 빈 등록

- [x] 7. SpecControlFilter + x-test-forge-* 파싱 — 메인 서버 (Requirements: 8, 9, 10, 11, 12, 13, 14)
  - `service/spec/SpecControlFilter.java` 생성
  - `SpecToolConverter`에서 x-test-forge-* 파싱 로직 추가 (6개 필드)
  - `ToolDefinition` record에 `control` 필드 추가 (ToolControl record)
  - SpecControlFilter.applyGlobalExclude() — global-exclude 설정 적용 (AntPathMatcher)
  - 잘못된 타입 → WARN 로그, 미정의 필드 → 무시
  - application.yml에 global-exclude 설정 구조 추가
  - SpecDetailResponse에 제외 API 목록 + 사유 포함
  - block된 API description에 "[BLOCKED: reason]" 추가

- [x] 8. RecipeSaverService 구현 — 대화 이력에서 레시피 생성 (Requirements: 4)
  - `service/recipe/RecipeSaverService.java` 생성
  - 세션 tool_call 이력 추출 + stepRange 필터링
  - step 간 참조 분석 (JSONPath 매핑)
  - 변수 타입 자동 할당 (참조/input/auto)
  - bodyStrategy 자동 결정
  - `POST /api/v1/recipes/generate-from-session` 엔드포인트
  - 요청 DTO: GenerateRecipeRequest

- [x] 9. RecipeSuggestionService 구현 — 유사 레시피 제안 (Requirements: 7)
  - `service/recipe/RecipeSuggestionService.java` 생성
  - name/description/tags 기반 유사도 검색 (LIKE + 태그 매칭)
  - `GET /api/v1/recipes/suggest?query=...` 엔드포인트 (최대 3개)
  - AgentLoopService에서 루프 시작 전 유사 레시피 확인 → SSE recipe_suggestion 이벤트

- [x] 10. 레시피 공유/복제 기능 (Requirements: 6)
  - `RecipeService.clone()` 구현
  - `POST /api/v1/recipes/{id}/clone` 엔드포인트
  - getAll() 수정: public 전체 + 본인 private
  - update/delete에 owner 확인 + 403 처리

- [x] 11. SseEventBufferService 구현 — 이벤트 버퍼링 및 재연결 지원 (Requirements: 15)
  - `service/agent/SseEventBufferService.java` 생성
  - 세션별 이벤트 버퍼 (ConcurrentHashMap + Deque)
  - publish(): SSE 전송 + 버퍼 저장 (이벤트 ID 자동 부여)
  - getEventsAfter(): 재연결 시 누락 이벤트 반환
  - 120초 초과 이벤트 정리 스케줄러
  - ChatController SSE 스트림에서 Last-Event-ID 헤더 처리
  - AgentLoopService의 SSE 이벤트 발행을 publish() 호출로 교체

- [x] 12. FE Agent Runner 상태 머신 — SSE 재연결 + 동시 Tool Call (Requirements: 15, 16)
  - `hooks/useAgentRunner.ts` — 상태 관리 (idle/running/paused/error)
  - `hooks/useSseConnection.ts` — SSE 연결/재연결 (Last-Event-ID, 지수 백오프, 3회 실패 시 수동 버튼)
  - localStorage 활성 세션 ID 저장/복원
  - 동시 tool_call: Promise.allSettled() 병렬 실행, 타임아웃 30초 (AbortController)
  - 결과 일괄 POST 전달 (성공/실패 분리)
  - `stores/agentRunnerStore.ts` 생성

- [x] 13. FE Auth Guard 구현 — 401 감지, 폴링, 재개, 타임아웃 (Requirements: 17)
  - `hooks/useAuthGuard.ts` 생성
  - 401 감지 → 일시정지 + 실행 컨텍스트 보존
  - auth profile에서 login-page-url 조회 → 링크 표시
  - 10초 간격 폴링으로 로그인 완료 감지
  - 5분 타임아웃 → 종료 + 에러 메시지
  - 멀티 서비스 시나리오 시작 전 사전 인증 체크

- [x] 14. FE Agent Runner — block/confirm 처리 (Requirements: 9, 10)
  - tool_call 수신 시 control 메타데이터 확인
  - blocked → 호출 차단 + 사유 표시 + BE에 실패 전달
  - confirm → 확인 팝업 (ToolCallConfirmDialog 컴포넌트)
  - readonly → confirm 스킵, 즉시 실행
  - 사용자 거부 → BE에 rejected 전달

- [x] 15. 2-Stage Strategy 보완 — fallback, group 필터링 (Requirements: 12, 18)
  - TwoStageFilterService에 그룹 기반 필터링 추가
  - Stage 1 빈 결과 fallback: 전체 서브도메인 요약 → AI 재선택
  - 에이전트 루프 중 추가 서브도메인 요청 시 동적 추가 (최대 1회)
  - fallback 후에도 실패 시 전체 tool 전달
  - threshold < 30이면 2-Stage 스킵

## Task Dependency Graph

```json
{
  "waves": [
    [1, 3, 6],
    [2, 4, 7, 11],
    [5, 8, 9, 10, 12],
    [13, 14, 15]
  ]
}
```

## Notes

- Task 1~5는 P0 (레시피 핵심 실행 엔진) — 먼저 완료해야 후속 작업 가능
- Task 6~7은 P1 (어노테이션) — 독립적으로 병렬 진행 가능
- Task 8~10은 P2 (생성 UX, 공유) — Task 1,5 완료 후 진행
- Task 11~15는 P3 (안정화) — 독립적으로 병렬 진행 가능
- FE 작업(Task 12~14)은 BE 작업(Task 11, 7) 완료 후 통합 테스트 필요
