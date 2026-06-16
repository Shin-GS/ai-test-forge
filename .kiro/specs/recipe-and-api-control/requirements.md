# Requirements Document

## Introduction

레시피 시스템 개선 및 API 제어 어노테이션 시스템을 구현한다. 레시피는 "AI가 실행한 API 호출 순서"를 저장해두고 AI 토큰 비용을 절감하면서 재실행하는 메커니즘이다. 사용자는 YAML 문법이나 내부 구조를 알 필요 없이 자연어 대화만으로 레시피를 생성/수정/실행한다. 실행 모드는 step의 body-strategy에 따라 자동 결정되며, AI 호출이 필요 없는 step은 토큰 비용 0으로 처리된다. 실행 전에는 현재 API 스펙과의 호환성을 자동 검증한다. API 제어 어노테이션은 서브도메인 개발자가 코드 레벨에서 테스트 도구의 API 동작을 제어하며, OpenAPI 확장 필드(`x-test-forge-*`)로 메인 서버에 전달된다. 추가로 에이전트 루프의 안정성을 높이기 위한 FE Agent Runner, 인증 플로우, 2-Stage Strategy 보완을 포함한다.

## Glossary

- **Recipe_Engine**: 레시피를 파싱하고 step별 변수 치환, 스펙 검증, HTTP 호출, 결과 추출을 순차적으로 수행하는 실행 엔진
- **Recipe_Step**: 레시피 내 하나의 API 호출 단위. subdomain, method, path, body, extract 정보를 포함
- **Body_Strategy**: step의 body 생성 방식을 결정하는 전략 (fixed, gen, ai-generate, ai-fill)
- **Select_Strategy**: 조회 결과에서 값을 선택하는 전략 (ai-pick)
- **Variable_Resolver**: 레시피 실행 시 `{{변수명}}`, `{{gen:*}}`, `{{input:*}}` 등의 변수를 실제 값으로 치환하는 모듈
- **Recipe_Saver**: 사용자의 대화 이력(tool_call 히스토리) 또는 자연어 요청에서 레시피를 생성하는 모듈
- **Spec_Validator**: 레시피 실행 전 각 step의 API가 현재 DB에 저장된 스펙과 호환되는지 검증하는 모듈
- **Annotation_Processor**: 서브도메인 서버의 어노테이션을 OpenAPI JSON의 x-test-forge-* 확장 필드로 변환하는 springdoc 확장
- **Global_Rule**: 메인 서버에서 설정 기반으로 API를 일괄 제외/차단하는 글로벌 안전 규칙
- **Agent_Runner**: FE에서 에이전트 루프의 tool_call을 수신하고 서브도메인 API를 호출한 뒤 결과를 BE에 전달하는 브라우저 측 실행기
- **Auth_Guard**: 서브도메인 API 호출 전후 인증 상태를 검사하고 401 발생 시 에이전트 루프를 일시정지하는 모듈

## Requirements

### Requirement 1: 레시피 실행

**User Story:** As a 사용자, I want to 저장된 레시피를 실행하여 테스트 데이터를 생성하고 싶다, so that 반복되는 시나리오를 빠르게 재실행할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 레시피 실행을 요청하면, THE Recipe_Engine SHALL step의 body-strategy를 기준으로 AI 호출 필요 여부를 자동 판단한다 (fixed/gen → AI 미호출, ai-generate/ai-fill/ai-pick → AI 호출)
2. WHEN a step의 body-strategy가 "fixed" 또는 "gen"이면, THE Recipe_Engine SHALL AI를 호출하지 않고 규칙 기반으로 직접 처리한다
3. WHEN a step의 body-strategy가 "ai-generate"이면, THE Recipe_Engine SHALL 해당 step의 endpoint request body 스키마와 이전 step에서 추출된 변수 값을 AI에게 전달하여 body 전체를 생성하도록 요청한다
4. WHEN a step의 body-strategy가 "ai-fill"이면, THE Recipe_Engine SHALL 값이 명시적으로 정의된 필드를 유지하고, 빈 필드만 AI에게 채우도록 요청한다
5. WHEN a step의 select-strategy가 "ai-pick"이면, THE Recipe_Engine SHALL 조회 결과 목록(최대 50건)과 ai-hint를 AI에게 전달하여 적합한 항목 1건을 선택하도록 요청한다
6. WHEN AI에게 body 생성을 요청할 때, THE Recipe_Engine SHALL 전체 API 목록이 아닌 해당 step의 endpoint 스키마만 전달한다
7. IF AI가 생성한 body가 스키마 검증에 실패하면, THEN THE Recipe_Engine SHALL 최대 2회 재생성을 요청하고, 재시도 후에도 실패하면 에러로 중단한다
8. WHEN 모든 step이 성공적으로 완료되면, THE Recipe_Engine SHALL 각 step의 서브도메인명, API 경로, HTTP 상태 코드, 추출된 변수명과 값을 포함한 실행 결과를 사용자에게 표시한다
9. IF a step 실행 중 HTTP 4xx 또는 5xx 응답이 발생하면, THEN THE Recipe_Engine SHALL 실패한 step 번호, 서브도메인명, API 경로, HTTP 상태 코드, 응답 본문을 표시하고 실행을 중단한다
10. WHILE 레시피 실행 중, THE Recipe_Engine SHALL 각 step의 진행 상태(대기/실행중/완료/실패)를 실시간으로 사용자에게 표시한다

### Requirement 2: 레시피 실행 전 스펙 검증

**User Story:** As a 사용자, I want to 레시피 실행 전에 API 변경으로 깨진 부분을 미리 알고 싶다, so that 실행 실패를 사전에 방지하고 레시피를 최신 스펙에 맞게 수정할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 레시피 실행을 요청하면, THE Spec_Validator SHALL 실행 전에 각 step의 API(method + path)가 현재 DB에 저장된 해당 서브도메인 스펙에 존재하는지 검증한다
2. WHEN a step의 API가 현재 스펙에 존재하면, THE Spec_Validator SHALL 레시피의 body 필드가 현재 스펙의 required 필드를 모두 포함하는지, 필드명이 일치하는지 검증한다
3. IF 스펙 검증에서 변경이 없으면(API 존재 + required 필드 일치), THEN THE Recipe_Engine SHALL 사용자에게 별도 안내 없이 즉시 실행을 시작한다
4. IF 스펙 검증에서 경미한 변경이 감지되면(선택 필드 추가 등 실행에 영향 없는 변경), THEN THE Recipe_Engine SHALL 변경 사항을 경고로 표시하고 실행 여부를 사용자에게 확인한다
5. IF 스펙 검증에서 호환 불가 변경이 감지되면(필수 필드 추가/변경, API 경로 변경, API 삭제), THEN THE Recipe_Engine SHALL 실행을 차단하고 변경된 부분을 상세히 안내하며, "AI로 자동 수정" 옵션을 제공한다
6. WHEN 사용자가 "AI로 자동 수정"을 선택하면, THE Recipe_Engine SHALL 현재 스펙을 기반으로 AI에게 레시피 수정안을 요청하고, 수정된 레시피를 사용자에게 확인 후 저장한다
7. WHEN 서브도메인 스펙이 갱신될 때, THE 메인 서버 SHALL 해당 서브도메인의 API를 사용하는 레시피 목록을 백그라운드로 검증하고, 호환 불가 레시피에 "스펙 변경으로 수정 필요" 표시를 추가한다

### Requirement 3: 레시피 변수 시스템

**User Story:** As a 사용자, I want to 레시피 실행 시 매번 다른 값을 주입하거나 이전 step 결과를 자동 연결하고 싶다, so that 하나의 레시피로 다양한 시나리오를 처리할 수 있다.

#### Acceptance Criteria

1. WHEN a 레시피에 `type: input`인 변수가 정의되어 있으면, THE Recipe_Engine SHALL 실행 시작 전에 사용자에게 해당 변수의 label과 입력 필드를 표시하여 값을 입력받는다
2. IF a 레시피 변수에 `required: true`가 설정되어 있고 사용자가 값을 입력하지 않으면, THEN THE Recipe_Engine SHALL 실행을 시작하지 않고 미입력된 변수명을 명시하는 필수 입력 안내를 표시한다
3. IF a 레시피 변수에 `required: false`가 설정되어 있고 사용자가 값을 입력하지 않으면, THEN THE Recipe_Engine SHALL 해당 변수를 빈 문자열로 치환하고 실행을 진행한다
4. THE Variable_Resolver SHALL `{{gen:email}}`은 `{랜덤8자}@test.com` 형식, `{{gen:koreanName}}`은 한국어 성+이름 2~3자, `{{gen:phone}}`은 `010-XXXX-XXXX` 형식(숫자 랜덤), `{{gen:uuid}}`는 UUID v4 형식으로 생성한다
5. IF a step의 body에 지원하지 않는 `{{gen:*}}` 타입이 포함되면, THEN THE Variable_Resolver SHALL 에러 메시지에 미지원 생성자 타입명을 포함하여 실행을 중단한다
6. WHEN a step의 extract 필드에 JSONPath 표현식이 지정되면, THE Recipe_Engine SHALL 해당 step의 HTTP 응답 body에서 값을 추출하여 지정된 변수명으로 저장하고 후속 step에서 `{{변수명}}`으로 참조 가능하게 한다
7. IF a JSONPath 표현식이 응답에서 값을 찾지 못하면, THEN THE Recipe_Engine SHALL 실행을 중단하고 에러 메시지에 실패한 JSONPath 표현식과 해당 step의 실제 응답 body를 포함하여 표시한다
8. IF a step의 body에 참조된 `{{변수명}}`이 이전 step의 extract 결과에도 input 변수에도 존재하지 않으면, THEN THE Recipe_Engine SHALL 해당 변수명을 포함한 해석 실패 메시지를 표시하고 실행을 중단한다

### Requirement 4: 레시피 생성 — 대화 이력 기반 저장

**User Story:** As a 사용자, I want to 방금 AI와 대화하며 실행한 API 호출 순서를 레시피로 저장하고 싶다, so that 다음부터는 토큰 비용 없이 동일 패턴을 재실행할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 "레시피로 저장"을 요청하면, THE Recipe_Saver SHALL 현재 세션의 tool_call 이력에서 성공한 호출들의 실행 순서를 추출하고, 전체 목록을 사용자에게 표시한다
2. WHEN 대화 이력에 여러 요청이 포함되어 있으면, THE Recipe_Saver SHALL 사용자에게 레시피에 포함할 범위를 선택하도록 요청한다 (예: "1~4번까지", "전체", "직접 선택")
3. IF 현재 세션에 성공한 tool_call 이력이 1건 미만이면, THEN THE Recipe_Saver SHALL 레시피 생성을 거부하고 tool_call 이력이 부족하다는 메시지를 전달한다
4. WHEN 범위가 결정되면, THE Recipe_Saver SHALL 선택된 호출들의 응답에서 이후 step의 파라미터로 사용된 값을 식별하고 JSONPath를 자동 매핑한다
5. WHEN 레시피를 생성할 때, THE Recipe_Saver SHALL params에서 이전 step 응답을 참조하는 값은 참조 변수 타입으로, 사용자가 직접 입력한 고유 식별값은 input 타입으로, 나머지 값은 auto 타입으로 할당한다
6. WHEN 레시피 생성이 완료되면, THE Recipe_Saver SHALL 생성된 레시피 구조(step 목록, 변수, 각 step의 AI 호출 필요 여부)를 사용자에게 자연어로 요약하여 확인을 요청한다
7. IF 사용자가 확인을 거부하면, THEN THE Recipe_Saver SHALL 저장하지 않고 대화를 계속한다
8. WHEN 사용자가 확인하면, THE Recipe_Saver SHALL 레시피를 DB에 저장하고 완료 메시지를 전달한다
9. THE Recipe_Saver SHALL 레시피에 name(최대 100자), description(최대 500자), tags(최대 10개), variables, steps(최대 30개) 필드를 포함한다

### Requirement 5: 레시피 생성 — 자연어 직접 정의

**User Story:** As a 사용자, I want to 실행 없이 자연어로 레시피를 직접 정의하고 싶다, so that API를 실행하지 않고도 반복 패턴을 미리 준비할 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 자연어로 레시피 생성을 요청하면(예: "회원 만들고 이력서 생성해서 지원하는 레시피 만들어줘"), THE Recipe_Saver SHALL 요청 내용을 분석하여 현재 등록된 서브도메인 스펙 기반으로 step 목록을 구성한다
2. WHEN 사용자가 AI 관여 범위를 자연어로 지정하면(예: "이력서 내용은 AI가 만들어줘"), THE Recipe_Saver SHALL 해당 step의 body-strategy를 ai-generate 또는 ai-fill로 설정한다
3. WHEN 사용자가 매번 입력할 값을 지정하면(예: "포지션 ID는 매번 내가 지정할게"), THE Recipe_Saver SHALL 해당 파라미터를 input 타입 변수로 설정한다
4. WHEN 레시피 구성이 완료되면, THE Recipe_Saver SHALL 구성된 flow를 사용자에게 자연어로 요약하여 확인을 요청한다 (내부 구조/YAML 표시 없이)
5. IF 사용자가 수정을 요청하면(예: "2번 step에서 경력을 항상 5년차로 고정해줘"), THEN THE Recipe_Saver SHALL 해당 변경을 반영하고 다시 확인을 요청한다
6. WHEN 사용자가 확인하면, THE Recipe_Saver SHALL 레시피를 DB에 저장한다
7. IF 사용자가 요청한 API가 등록된 스펙에 존재하지 않으면, THEN THE Recipe_Saver SHALL 해당 API를 찾을 수 없음을 안내하고 유사한 API를 제안한다

### Requirement 6: 레시피 공유 및 관리

**User Story:** As a 팀원, I want to 다른 사람이 만든 레시피를 보고 실행하고 싶다, so that 팀 전체가 검증된 테스트 패턴을 공유하고 재사용할 수 있다.

#### Acceptance Criteria

1. THE Recipe_Engine SHALL 레시피의 공개 범위를 public(기본값)과 private 두 가지로 지원한다
2. WHEN 레시피가 public으로 설정되면, THE Recipe_Engine SHALL 모든 사용자가 해당 레시피를 조회하고 실행할 수 있도록 한다
3. WHEN 레시피가 private으로 설정되면, THE Recipe_Engine SHALL 생성자(owner)만 해당 레시피를 조회하고 실행할 수 있도록 한다
4. THE Recipe_Engine SHALL 레시피의 수정 및 삭제 권한을 생성자(owner)에게만 부여한다
5. WHEN 사용자가 다른 사용자의 public 레시피를 복제하면, THE Recipe_Engine SHALL 복제된 레시피를 요청한 사용자를 owner로 하는 새 레시피로 저장한다
6. WHEN 사용자가 레시피를 수정하고 싶은데 owner가 아니면, THE Recipe_Engine SHALL "복제 후 수정" 옵션을 제안한다

### Requirement 7: 레시피 자동 제안

**User Story:** As a 사용자, I want to 채팅 시 관련 레시피가 있으면 AI가 알려주길 원한다, so that 이미 만들어진 레시피를 모르고 처음부터 대화하는 비효율을 줄일 수 있다.

#### Acceptance Criteria

1. WHEN 사용자가 채팅으로 작업을 요청하면, THE 메인 서버 SHALL 요청 내용과 유사한 레시피가 존재하는지 검색한다
2. IF 유사한 레시피가 존재하면, THEN THE 메인 서버 SHALL 사용자에게 해당 레시피를 제안하고 "레시피로 실행(빠름)" 또는 "새로 대화로 진행" 선택지를 제공한다
3. WHEN 사용자가 "레시피로 실행"을 선택하면, THE Recipe_Engine SHALL 해당 레시피의 실행 플로우(변수 입력 → 스펙 검증 → 실행)를 시작한다
4. WHEN 사용자가 "새로 대화로 진행"을 선택하면, THE 메인 서버 SHALL 일반 에이전트 루프를 시작한다
5. THE 메인 서버 SHALL 레시피 제안 시 레시피명, 설명, step 수, AI 사용 여부를 요약하여 표시한다

### Requirement 8: API 제어 어노테이션 — 수집 제외

**User Story:** As a 서브도메인 개발자, I want to 특정 API를 AI 테스트 도구에서 완전히 숨기고 싶다, so that 민감하거나 위험한 API가 AI에게 노출되지 않는다.

#### Acceptance Criteria

1. WHEN a 서브도메인 서버의 API 메서드에 `@TestForgeExclude` 어노테이션이 적용되면, THE Annotation_Processor SHALL OpenAPI JSON의 해당 operation에 `x-test-forge-exclude: true` 확장 필드를 추가한다
2. WHEN 메인 서버가 OpenAPI JSON을 파싱할 때 `x-test-forge-exclude: true`인 operation을 발견하면, THE 메인 서버 SHALL 해당 API를 tool 목록에서 제거하고 AI 시스템 프롬프트에도 포함하지 않는다
3. THE Annotation_Processor SHALL `@TestForgeExclude`의 reason 파라미터를 선택적으로 지원하며, 지정된 경우 `x-test-forge-exclude` 필드를 `{ "reason": "사유" }` 객체 형태로 변환한다
4. WHEN `@TestForgeExclude`가 컨트롤러 클래스 레벨에 적용되면, THE Annotation_Processor SHALL 해당 클래스의 모든 API 메서드에 exclude를 일괄 적용한다

### Requirement 9: API 제어 어노테이션 — 실행 차단

**User Story:** As a 서브도메인 개발자, I want to 특정 API를 AI에게 보여주되 실행은 차단하고 싶다, so that AI가 의존 관계를 파악하면서도 위험한 API 실행을 방지할 수 있다.

#### Acceptance Criteria

1. WHEN a API에 `@TestForgeBlock` 어노테이션이 적용되면, THE Annotation_Processor SHALL OpenAPI JSON에 `x-test-forge-block` 확장 필드를 추가하고, reason 파라미터가 지정된 경우 해당 값을 포함한다
2. WHEN Agent_Runner가 `x-test-forge-block`이 설정된 API를 호출하려 하면, THE Agent_Runner SHALL 호출을 실행하지 않고, 차단 사실과 reason(미지정 시 "이 API는 실행이 차단되어 있습니다")을 사용자에게 표시한다
3. WHEN Agent_Runner가 block된 API 호출을 차단하면, THE Agent_Runner SHALL 차단 결과를 BE에 tool_call 실패로 전달하여 AI가 대체 방안을 판단하도록 한다
4. WHILE 에이전트 루프 실행 중, THE 메인 서버 SHALL block된 API를 AI tool 목록에 포함하되 description 끝에 "[BLOCKED: {reason}]" 형태로 차단 사유를 추가한다

### Requirement 10: API 제어 어노테이션 — 실행 전 확인

**User Story:** As a 서브도메인 개발자, I want to 특정 API 실행 전에 사용자 확인을 받도록 하고 싶다, so that 부작용이 있는 API를 실수로 실행하는 것을 방지할 수 있다.

#### Acceptance Criteria

1. WHEN a API에 `@TestForgeConfirm` 어노테이션이 적용되면, THE Annotation_Processor SHALL OpenAPI JSON에 `x-test-forge-confirm` 확장 필드를 `{ "message": "<message 파라미터 값>" }` 형태로 추가한다 (message 미지정 시 빈 문자열)
2. WHEN Agent_Runner가 `x-test-forge-confirm`이 설정된 API를 호출하려 하면, THE Agent_Runner SHALL 에이전트 루프를 일시정지하고, API의 HTTP 메서드, 경로, 요청 파라미터, confirm message를 포함하는 확인 팝업을 표시한다
3. WHEN 사용자가 확인을 승인하면, THE Agent_Runner SHALL 에이전트 루프를 재개하고 해당 API를 실행한다
4. WHEN 사용자가 확인을 거부하면, THE Agent_Runner SHALL 거부 사실 및 API 정보를 BE에 전달하여 AI가 대체 방안을 판단하도록 한다
5. IF 확인 팝업 표시 후 사용자가 응답하지 않으면, THEN THE Agent_Runner SHALL 사용자가 응답할 때까지 일시정지 상태를 유지한다

### Requirement 11: API 제어 어노테이션 — 안전 표시 및 힌트

**User Story:** As a 서브도메인 개발자, I want to API에 안전 표시나 AI 전용 힌트를 부여하고 싶다, so that 읽기 전용 API는 확인 없이 빠르게 실행되고 AI가 올바른 맥락으로 API를 사용할 수 있다.

#### Acceptance Criteria

1. WHEN a API에 `@TestForgeReadOnly` 어노테이션이 적용되면, THE Annotation_Processor SHALL OpenAPI JSON에 `x-test-forge-readonly: true` 확장 필드를 추가한다
2. WHEN Agent_Runner가 `x-test-forge-readonly: true`가 설정된 API를 호출할 때, THE Agent_Runner SHALL `x-test-forge-confirm`이 동시에 설정되어 있더라도 확인 없이 즉시 실행한다
3. WHEN a API에 `@TestForgeHint` 어노테이션이 적용되면, THE Annotation_Processor SHALL value 파라미터 값을 `x-test-forge-hint` 확장 필드(문자열, 최대 500자)로 추가한다
4. WHEN 메인 서버가 AI에게 tool 목록을 전달할 때, THE 메인 서버 SHALL hint가 있는 API의 description 끝에 "[AI Hint: {힌트 내용}]" 형식으로 힌트를 추가한다
5. IF `@TestForgeReadOnly`와 `@TestForgeBlock`이 동시에 적용되면, THEN `x-test-forge-block`을 우선 적용하여 호출을 차단한다

### Requirement 12: API 제어 어노테이션 — 그룹핑

**User Story:** As a 서브도메인 개발자, I want to API를 논리적 그룹으로 묶고 싶다, so that 2-Stage Strategy에서 관련 API만 효율적으로 필터링할 수 있다.

#### Acceptance Criteria

1. WHEN a API에 `@TestForgeGroup` 어노테이션이 적용되면, THE Annotation_Processor SHALL OpenAPI JSON에 `x-test-forge-group` 확장 필드를 배열 형태로 추가하며, 그룹명은 영숫자와 하이픈으로 구성된 최대 50자 문자열이어야 한다
2. THE Annotation_Processor SHALL 하나의 API가 최대 10개 그룹에 소속되도록 복수 그룹 지정을 지원한다
3. WHEN 2-Stage Strategy의 Stage 1에서 관련 API를 필터링할 때, THE 메인 서버 SHALL 각 서브도메인의 그룹 목록을 AI에게 전달하여 필터링 판단 기준으로 활용한다
4. IF a API에 `@TestForgeGroup`이 적용되지 않으면, THEN THE 메인 서버 SHALL 해당 API를 ungrouped로 취급하고, Stage 2에서 AI가 명시적으로 요청한 경우에만 tool 목록에 포함한다

### Requirement 13: 글로벌 API 제어 규칙

**User Story:** As a 관리자, I want to 메인 서버 설정으로 특정 패턴의 API를 일괄 제외하고 싶다, so that 어노테이션 없이도 위험한 API를 전역적으로 차단할 수 있다.

#### Acceptance Criteria

1. THE 메인 서버 SHALL spec-registry.global-exclude 설정에서 methods, path-patterns, tags 기준으로 하나라도 매칭되는 API를 tool 목록에서 제거한다
2. WHEN a API가 global-exclude의 methods 목록에 해당하는 HTTP 메서드를 사용하면, THE 메인 서버 SHALL 해당 API를 tool 목록에서 제거한다
3. WHEN a API의 경로가 global-exclude의 path-patterns 중 하나와 Ant-style 패턴 매칭으로 일치하면, THE 메인 서버 SHALL 해당 API를 tool 목록에서 제거한다
4. WHEN a API의 OpenAPI tags 중 하나라도 global-exclude의 tags 목록에 포함되면, THE 메인 서버 SHALL 해당 API를 tool 목록에서 제거한다
5. THE 메인 서버 SHALL 글로벌 제외 규칙과 어노테이션 규칙을 OR 합집합으로 적용하되, 글로벌 규칙에 의해 제외된 API는 어노테이션으로 재포함할 수 없다
6. IF global-exclude 설정이 모두 비어있거나 미설정이면, THEN THE 메인 서버 SHALL 글로벌 제외를 적용하지 않는다
7. IF path-patterns에 유효하지 않은 패턴이 포함되면, THEN THE 메인 서버 SHALL 해당 패턴을 무시하고 경고 로그를 기록한다
8. THE 메인 서버 SHALL 서브도메인 상세 페이지에서 제외된 API 목록과 제외 사유(어노테이션/글로벌 규칙)를 표시한다

### Requirement 14: OpenAPI 확장 필드 파싱

**User Story:** As a 메인 서버 개발자, I want to OpenAPI JSON의 x-test-forge-* 확장 필드를 파싱하여 제어 동작을 수행하고 싶다, so that Java 외 서버도 동일한 제어 메커니즘을 사용할 수 있다.

#### Acceptance Criteria

1. WHEN 메인 서버가 OpenAPI JSON을 파싱할 때, THE 메인 서버 SHALL 각 operation object에서 `x-test-forge-exclude`(boolean 또는 object), `x-test-forge-block`(object), `x-test-forge-confirm`(object), `x-test-forge-readonly`(boolean), `x-test-forge-hint`(string), `x-test-forge-group`(문자열 배열) 확장 필드를 파싱하여 제어 메타데이터로 저장한다
2. WHEN 비-Java 서버가 OpenAPI JSON에 x-test-forge-* 필드를 직접 작성하면, THE 메인 서버 SHALL 어노테이션 기반 서버와 동일하게 제어 동작을 적용한다
3. IF 확장 필드의 값이 기대 타입과 일치하지 않으면, THEN THE 메인 서버 SHALL 해당 필드를 무시하고 서브도메인 이름, API 경로, 필드명을 포함하는 경고 로그를 기록한다
4. IF 정의된 6개 필드 외의 `x-test-forge-` 접두사 필드가 존재하면, THEN THE 메인 서버 SHALL 해당 필드를 무시하고 정상적으로 파싱을 계속한다

### Requirement 15: FE Agent Runner 안정화 — 세션 복구

**User Story:** As a 사용자, I want to 브라우저 탭이 닫히거나 새로고침되어도 진행 중이던 에이전트 루프를 복구하고 싶다, so that 긴 시나리오 실행 중 브라우저 이슈로 작업을 잃지 않는다.

#### Acceptance Criteria

1. WHEN 브라우저 탭이 새로고침되거나 재방문되면, THE Agent_Runner SHALL 로컬 스토리지의 활성 세션 ID를 기반으로 SSE 스트림에 재연결하고 누락된 이벤트를 수신하여 UI에 반영한다
2. WHEN SSE 재연결 시, THE Agent_Runner SHALL Last-Event-ID 헤더를 포함하여 해당 ID 이후의 누락된 이벤트를 순서대로 수신한다
3. WHEN 에이전트 루프가 tool_call 대기 중 재연결되면, THE Agent_Runner SHALL 대기 중인 tool_call을 재수신하고 서브도메인 API를 재호출하여 결과를 전달한다
4. IF SSE 재연결이 3회 연속 실패하면, THEN THE Agent_Runner SHALL 연결 실패 메시지와 수동 재연결 버튼을 제공한다
5. WHILE Agent Loop 실행 중, THE 메인 서버 SHALL FE의 SSE 연결 상태와 무관하게 루프를 계속하고 미전달 이벤트를 최대 120초간 버퍼링한다
6. THE Agent_Runner SHALL SSE 재연결 시 초기 1초부터 배율 2.0의 지수 백오프로 시도하되 최대 간격은 10초를 초과하지 않는다

### Requirement 16: FE Agent Runner 안정화 — 동시 Tool Call 처리

**User Story:** As a 사용자, I want to AI가 한 턴에 여러 tool_call을 보낸 경우에도 안정적으로 처리되길 원한다, so that 병렬 API 호출 시나리오에서 에이전트 루프가 정상 동작한다.

#### Acceptance Criteria

1. WHEN AI가 한 턴에 2개 이상 최대 5개의 tool_call을 반환하면, THE Agent_Runner SHALL 모든 tool_call을 동시에 시작하고 모든 호출이 완료될 때까지 대기한다
2. WHEN 동시 실행된 모든 tool_call이 완료되면, THE Agent_Runner SHALL 각 결과를 단일 POST 요청으로 BE에 일괄 전달한다
3. IF 일부 tool_call이 실패하면, THEN THE Agent_Runner SHALL 성공/실패 결과를 함께 단일 요청으로 BE에 전달하여 AI가 판단하도록 한다
4. IF 개별 호출이 타임아웃 30초를 초과하면, THEN THE Agent_Runner SHALL 해당 호출을 타임아웃 실패로 처리하고 나머지 결과와 함께 전달한다

### Requirement 17: 인증 플로우 안정화

**User Story:** As a 사용자, I want to 서브도메인 API 호출 중 401이 발생해도 로그인 후 자동으로 에이전트 루프가 재개되길 원한다, so that 인증 만료가 작업 흐름을 완전히 중단시키지 않는다.

#### Acceptance Criteria

1. WHEN 서브도메인 API 호출에서 401 응답이 발생하면, THE Agent_Runner SHALL 에이전트 루프를 일시정지하고 실행 컨텍스트를 보존한 뒤 로그인 페이지 링크를 표시한다
2. WHEN 사용자가 로그인을 완료하면, THE Auth_Guard SHALL 인증 확인 요청을 재시도하여 401이 아닌 응답 수신 시 로그인 완료로 판정하고 실패 지점부터 재개한다
3. WHEN 멀티 서비스 시나리오 실행 시작 시, THE Agent_Runner SHALL 관련 서브도메인들의 인증 상태를 사전 체크한다
4. IF 사전 체크에서 미인증 서브도메인이 발견되면, THEN THE Agent_Runner SHALL 실행을 보류하고 미인증 서브도메인별 로그인 링크를 목록으로 표시한다
5. IF 인증 대기 상태에서 5분 이내에 로그인이 완료되지 않으면, THEN THE Agent_Runner SHALL 에이전트 루프를 종료하고 인증 타임아웃 에러를 표시한다

### Requirement 18: 2-Stage Strategy 보완

**User Story:** As a 사용자, I want to Stage 1 필터링이 실패해도 에이전트 루프가 정상 동작하길 원한다, so that API 필터링 오류가 전체 작업 실패로 이어지지 않는다.

#### Acceptance Criteria

1. IF Stage 1 필터링에서 관련 서브도메인을 찾지 못하면, THEN THE 메인 서버 SHALL 전체 서브도메인의 이름, description, 그룹 목록을 AI에게 전달하여 재선택을 요청한다
2. IF 에이전트 루프 중 AI가 추가 서브도메인을 요청하면, THEN THE 메인 서버 SHALL 요청된 서브도메인의 API를 tool 목록에 추가하는 재필터링을 최대 1회 수행한다
3. THE 메인 서버 SHALL 활성 tool 총 개수가 threshold(기본값 30개) 미만이면 2-Stage를 적용하지 않고 전체 tool을 직접 전달한다
4. IF fallback 후에도 AI가 서브도메인을 선택하지 못하면, THEN THE 메인 서버 SHALL 전체 tool을 AI에게 전달하고 루프를 계속한다
