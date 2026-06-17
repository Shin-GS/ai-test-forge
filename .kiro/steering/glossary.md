---
inclusion: always
---

# 프로젝트 용어 사전 (Glossary)

모든 에이전트는 아래 용어를 통일하여 사용한다. 새 용어 추가 시 이 문서를 업데이트한다.

## 사용자 유형

| 용어 | 설명 | 사용 금지 표현 |
|------|------|---------------|
| 사용자 (User) | 채팅 UI를 통해 테스트 데이터를 생성하는 사람 | 고객, 클라이언트, 테스터 |
| 관리자 (Admin) | AI Test Forge 시스템을 관리하는 운영자 | 어드민, 매니저 |

## 핵심 도메인 용어

| 용어 | 영문 | 설명 |
|------|------|------|
| 메인 서버 | Main Server | 채팅 UI 제공, API 스펙 관리, AI 에이전트 루프를 오케스트레이션하는 중앙 서버 |
| 서브도메인 서버 | Subdomain Server | 자신의 OpenAPI 스펙을 메인 서버에 등록하는 마이크로서비스 서버 |
| 클라이언트 라이브러리 | Client Library | 서브도메인 서버에 의존성으로 추가하면 API 스펙을 자동 등록해주는 Spring Boot Starter |
| AI 모듈 | AI Module | 인터페이스 기반의 교체 가능한 AI 서비스 (OpenAI, Claude 등) |
| API 스펙 | API Spec | 서브도메인 서버의 엔드포인트를 기술한 OpenAPI/Swagger JSON 문서 |
| 에이전트 루프 | Agent Loop | AI가 작업 완료까지 반복적으로 API를 호출하는 핵심 메커니즘 |
| 툴 | Tool | AI에게 호출 가능한 함수로 노출되는 API 엔드포인트 |
| 툴 콜 | Tool Call | AI가 특정 API 엔드포인트를 호출하겠다는 요청 |
| 채팅 세션 | Chat Session | 사용자와 AI 간의 대화 단위 |
| 서브도메인 인증 | Subdomain Auth | 각 서브도메인 서버의 인증 메커니즘 (토큰 기반) |
| 인증 토큰 | Auth Token | 서브도메인 서버 로그인으로 획득한 토큰, API 호출 시 사용 |
| 2단계 전략 | 2-Stage Strategy | 의도 파악 → API 필터링 → 실행 전략 (API 수가 많을 때) |
| 환경 | Environment | 같은 서비스의 다른 배포 인스턴스 식별자 (dev, feature-login 등) |
| Heartbeat | Heartbeat | 클라이언트 라이브러리가 주기적으로 메인 서버에 보내는 생존 신호 |
| 레시피 | Recipe | 자주 사용하는 API 호출 패턴을 저장/재사용하는 템플릿 |
| 워크스페이스 | Workspace | 유저가 현재 작업할 서브도메인 + 환경 세트를 묶은 단위 |
| 퀵 액션 | Quick Action | 채팅 화면에 버튼으로 노출되는 자주 쓰는 레시피/명령 |
| 인증 프로필 | Auth Profile | 서브도메인의 로그인 방식 메타 정보 (이름 + 로그인 페이지 URL) |

## API 스펙 상태

| 상태 | 설명 |
|------|------|
| REGISTERING | 비동기 파싱 처리 중 (대형 스펙) |
| REGISTERED | API 스펙이 메인 서버에 정상 등록됨 (미사용, 예약) |
| ACTIVE | 서브도메인 서버 동작 중, 스펙 최신 상태 |
| STALE | 갱신되지 않은 스펙 (서버 다운 가능성) |

## 채팅 세션 상태

| 상태 | 설명 |
|------|------|
| ACTIVE | 대화 진행 중 |
| COMPLETED | AI가 작업을 완료함 |
| WAITING | AI가 사용자에게 추가 정보를 요청 중 |

## AI 모델 티어 용어

| 용어 | 영문 | 설명 |
|------|------|------|
| 모델 티어 | Model Tier | 작업 난이도에 따라 AI 모델을 분류하는 개념 (reasoning, fast) |
| Reasoning 모델 | Reasoning Model | 고성능 AI 모델. Agent Loop 메인 대화 (의도 파악 + tool 선택 + 크로스 서비스 오케스트레이션)에 사용 |
| Fast 모델 | Fast Model | 경량 AI 모델. 2-Stage 필터, 레시피 Body 생성, "다음 액션" 힌트 생성 등 단순 작업에 사용 |
| Fallback | Fallback | Fast 모델 실패 시 Reasoning 모델로 자동 재시도하는 메커니즘 (Body 생성에만 적용) |

## 기술 용어

| 용어 | 설명 | 사용 금지 표현 |
|------|------|---------------|
| 메인 서버 | ai-test-forge Spring Boot 애플리케이션 | 코어 서버, 허브 |
| 클라이언트 라이브러리 | 서브도메인 등록용 Spring Boot Starter | SDK, 플러그인 |
| OpenAPI JSON | 서브도메인의 Swagger docs 엔드포인트에서 가져온 원본 JSON | Swagger 파일, API 문서 |
| OTP | One-Time Password, TOTP 기반 2단계 인증 | 2FA 코드 |
| Rate Limiter | 로그인/OTP 시도 횟수를 제한하는 인메모리 모듈 (brute-force 방지) | 속도 제한기 |
| 런타임 설정 | 서버 재시작 없이 변경 가능한 AI/Agent Loop 설정 (SettingsService) | 동적 설정 |

## 레시피 용어

| 용어 | 영문 | 설명 |
|------|------|------|
| 레시피 엔진 | Recipe Engine | 레시피를 파싱하고 step별 변수 치환, 스펙 검증, HTTP 호출, 결과 추출을 순차 수행하는 실행 엔진 |
| 레시피 스텝 | Recipe Step | 레시피 내 하나의 API 호출 단위 (subdomain, method, path, body, extract) |
| 바디 전략 | Body Strategy | step의 body 생성 방식 (fixed, gen, ai-generate, ai-fill) |
| 선택 전략 | Select Strategy | 조회 결과에서 값을 선택하는 전략 (ai-pick) |
| 변수 치환기 | Variable Resolver | 레시피 실행 시 `{{변수명}}`, `{{gen:*}}` 등을 실제 값으로 치환하는 모듈 |
| 스펙 검증기 | Spec Validator | 레시피 실행 전 각 step의 API가 현재 스펙과 호환되는지 검증하는 모듈 |
| 레시피 저장기 | Recipe Saver | 대화 이력(tool_call 히스토리)에서 레시피를 자동 생성하는 모듈 |

## API 제어 용어

| 용어 | 영문 | 설명 |
|------|------|------|
| API 제어 어노테이션 | TestForge Annotations | 서브도메인 서버의 어노테이션을 통해 AI 테스트 도구의 API 동작을 제어하는 메커니즘 |
| 글로벌 규칙 | Global Rule | 메인 서버 설정으로 API를 일괄 제외/차단하는 관리자 규칙 |
| 에이전트 러너 | Agent Runner | FE에서 에이전트 루프의 tool_call을 수신하고 서브도메인 API를 호출한 뒤 결과를 BE에 전달하는 브라우저 측 실행기 |
| 인증 가드 | Auth Guard | 서브도메인 API 호출 시 인증 상태를 감시하고 401 발생 시 에이전트 루프를 일시정지하는 모듈 |
| SSE 이벤트 버퍼 | SSE Event Buffer | SSE 연결 끊김 시 미전달 이벤트를 임시 보관하고 재연결 시 재전송하는 서버 측 버퍼 |

## 용어 사용 규칙

1. 모든 에이전트는 이 용어를 동일하게 사용한다.
2. 새 기능 추가 시 이 문서에 용어를 먼저 등록한다.
3. 영문 표기는 코드/파일명에, 한글 표기는 문서/커뮤니케이션에 사용한다.
4. 사용 금지 표현이 명시된 경우 해당 표현을 사용하지 않는다.
