# AI Test Forge

AI 기반 테스트 데이터 생성 플랫폼.

여러 서브도메인 프로젝트(마이크로서비스)의 테스트 데이터를 **자연어 채팅**으로 생성하는 오픈소스 도구입니다.

## 왜 필요한가?

마이크로서비스 환경에서 테스트 데이터를 만드는 건 고통입니다.

**기존 방식의 문제:**
- "입사지원" 하나 테스트하려면 회원 생성 → 이력서 생성 → 포지션 확인 → 지원 API 호출... Postman에서 4개 서비스, 10개 API를 순서대로 호출해야 함
- 서비스 간 의존성을 개발자가 머릿속에 기억하고 있어야 함
- 테스트 환경 초기화할 때마다 같은 작업 반복
- 새 팀원이 오면 이 과정을 처음부터 알려줘야 함

**AI Test Forge가 해결하는 것:**
- "입사지원 테스트 데이터 만들어줘" 한 마디면 AI가 서비스 간 의존성을 파악하고 순서대로 API 호출
- 부족한 정보는 AI가 알아서 채우거나 사용자에게 물어봄
- 자주 쓰는 패턴은 레시피로 저장해두고 한 번에 재실행
- 누구나 자연어로 복잡한 테스트 시나리오를 구성 가능

**대상:** 백엔드 개발자, QA 엔지니어 (dev/qa 환경에서 테스트 데이터 생성용)

> 💡 이 방식이 마음에 든다면, 특정 기능(자연어 → API 오케스트레이션)을 떼어내 운영 환경의 자동화 도구로 활용할 수도 있습니다.

## 핵심 기능

| 기능 | 설명 |
|------|------|
| 채팅 기반 생성 | 자연어 → 멀티 API 자동 호출로 테스트 데이터 생성 |
| AI Agent Loop | 작업 완료까지 AI가 반복적으로 API 호출 (SSE로 실시간 진행 표시) |
| 자동 서버 등록 | 클라이언트 라이브러리 추가만으로 OpenAPI 스펙 자동 등록 + heartbeat 기반 생존 관리 |
| 레시피 | 자주 쓰는 API 호출 패턴 저장/재사용 ("입사지원 레시피 실행해줘") |
| 서브도메인 인증 | 서브도메인별 로그인/토큰 관리, API 호출 시 자동 부착 |
| AI 모듈 교체 | 인터페이스 기반 — OpenAI, Claude, 커스텀 구현체 자유 교체 |
| 환경 분리 | K8s feature 브랜치별 서버도 자동 인식 (environment 태그) |

## UI 구조

```
┌─────────────────────────────────────────────────────┐
│  [💬 채팅]  [📡 서브도메인]  [📋 레시피]  [⚙️ 설정]   │
├─────────────────────────────────────────────────────┤
│                                                     │
│  채팅: 자연어로 조작 (메인)                           │
│  서브도메인: 등록된 서버 목록/상태/API 탐색            │
│  레시피: 저장된 패턴 목록/검색/실행                    │
│  설정: AI 프로바이더, 인증, Agent Loop 파라미터       │
│                                                     │
└─────────────────────────────────────────────────────┘
```

- **조작은 채팅 중심** — 모든 실행/설정 변경은 채팅에서 가능
- **나머지 탭은 조회/탐색** — 뭐가 있는지 보고, 클릭하면 채팅으로 연결
- 서버 15+, 레시피 100+에서도 검색/필터로 탐색 가능

## 아키텍처

```
[서브도메인 서버 A] ──push + heartbeat──→ [메인 서버 (AI Test Forge)]
[서브도메인 서버 B] ──push + heartbeat──→ [메인 서버 (AI Test Forge)]
[서브도메인 서버 C] ──push + heartbeat──→       ↕ SSE (실시간)
                                          [사용자 브라우저 - 채팅 UI]
```

- 서브도메인 서버: 시작 시 자동 등록, 주기적 heartbeat로 생존 신호
- 메인 서버: API 스펙 관리 + AI Agent Loop 실행 + 서브도메인 API 호출
- 클라이언트: SSE로 Agent Loop 진행 상태 실시간 수신

### 구성요소 3개

| 구성요소 | 설명 |
|---------|------|
| **메인 서버** | 채팅 UI + AI Agent Loop + API 스펙 관리 + 레시피 |
| **클라이언트 라이브러리** | 서브도메인에 의존성 추가 → 시작 시 자동 등록 + heartbeat |
| **AI 모듈** | 인터페이스 기반, 구현체 교체 가능 (OpenAI, Claude 등) |

## 기술 스택

| 구분 | 기술 |
|------|------|
| 메인 서버 | Java 21+ / Spring Boot 3.x / Gradle |
| 웹 UI | React 19 / TypeScript / Vite / Tailwind CSS 4 |
| 실시간 | SSE (Server-Sent Events) |
| 클라이언트 라이브러리 | Java / Spring Boot Starter |
| 데이터베이스 | MySQL 8.x |
| AI | OpenAI, Claude (인터페이스 기반 교체 가능) |

## 프로젝트 구조

```
ai-test-forge/
├── packages/
│   ├── server/              # Spring Boot 메인 서버
│   ├── web/                 # React 채팅 UI (탭 기반)
│   └── client-spring/       # Spring Boot Starter 클라이언트 라이브러리
├── docs/
│   ├── design/              # 페이지별 디자인 명세 (HTML)
│   └── test/                # QA 테스트 체크리스트
└── .kiro/                   # AI 개발 도구 설정
```

---

## 빠른 시작 (Quick Start)

### 사전 요구사항

- Java 21+
- Node.js 20+ & pnpm
- MySQL 8.x
- (선택) OpenAI API Key 또는 Claude API Key

### 1. 환경 설정

```bash
git clone https://github.com/your-org/ai-test-forge.git
cd ai-test-forge
cp .env.example .env
# .env 파일에서 DB 접속 정보, AI API 키 설정
```

### 2. 데이터베이스 생성

```sql
CREATE DATABASE ai_test_forge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 서버 + UI 실행

```bash
# 메인 서버
cd packages/server && ./gradlew bootRun

# 웹 UI (별도 터미널)
cd packages/web && pnpm install && pnpm dev
```

브라우저에서 `http://localhost:5173` 접속 → 채팅 시작!

---

## 서브도메인 서버 연동

### 1. 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.aitestforge:client-spring:0.1.0")
}
```

### 2. 설정

```yaml
# application.yml
ai-test-forge:
  server-url: http://localhost:8080       # 메인 서버 주소
  subdomain-name: user-service            # 서브도메인 식별명 (필수)
  base-url: http://localhost:8081          # 이 서버에 접근 가능한 URL (필수)
  enabled: true
  profiles: dev, qa                        # 이 프로필에서만 활성화
  docs-url: /v3/api-docs                  # OpenAPI docs URL
  environment: ${BRANCH_NAME:default}     # 환경 식별 (K8s namespace 등)
  heartbeat-interval: 30s                 # heartbeat 주기
```

### 3. 서버 시작

시작하면 자동으로:
1. OpenAPI 스펙을 메인 서버에 등록
2. 주기적 heartbeat 전송 (생존 신호)
3. 서버 종료 시 heartbeat 중단 → 메인 서버가 자동으로 STALE → 삭제

> **K8s 환경**: feature 브랜치별 서버도 `environment`로 자동 구분됩니다. 같은 `user-service`라도 `feature-login`과 `dev`는 별개로 등록.

---

## 레시피 (반복 패턴 저장)

```
사용자: "방금 한 작업을 레시피로 저장해줘"
AI:     ✅ "입사지원 데이터 생성" 레시피 저장 완료 (3단계)

사용자: "입사지원 레시피 실행해줘"
AI:     포지션 ID를 알려주세요.
사용자: "123"
AI:     ✅ 레시피 실행 완료
        - 회원 생성 (ID: 456)
        - 이력서 생성 (ID: 789)
        - 포지션 123에 지원 완료
```

- 대화에서 자동 생성 또는 수동 등록
- 파라미터화 가능 (매번 다른 값 주입)
- 레시피 탭에서 목록 확인/검색/실행

---

## AI 모듈 교체

```yaml
ai:
  provider: openai    # openai | claude | mock
```

커스텀 구현체:
```java
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "my-custom")
public class MyCustomAiService implements AiService {
    @Override
    public AiChatResponse chat(List<ChatMessage> messages, List<ToolDefinition> tools) {
        // 커스텀 AI 호출
    }
}
```

---

## 프로필 전략

| Profile | AI 서비스 | 클라이언트 라이브러리 | 용도 |
|---------|----------|-------------------|------|
| local | Mock (API 호출 없음) | localhost 등록 | 로컬 개발 |
| dev | Real AI | dev 서버 등록 | 통합 테스트 |
| prod | Real AI | **비활성화** | 프로덕션 |

---

## 설계 원칙

- **AI 모델 비강제** — 인터페이스 + 설정으로 교체 가능
- **오픈소스 친화** — fork해서 최소 수정으로 자기 환경 적용
- **프로필 기반 제어** — dev/qa에서만 동작, prod 자동 비활성화
- **Swagger 비의존** — 클라이언트 라이브러리가 HTTP로 raw JSON만 전달
- **자동 관리** — heartbeat 기반으로 서버 등록/삭제 자동화 (K8s 대응)
- **텍스트 분리** — UI 텍스트를 상수로 분리, fork 시 언어 교체 용이

---

## 사용 예시

```
사용자: "개인회원으로 가입해줘"
AI:     이메일과 이름을 정할까요, 아니면 자동 생성할까요?
사용자: "자동으로 해줘"
AI:     ✅ 개인회원 생성 완료
        - 이메일: test_user_1@example.com
        - 이름: 김테스트
        - ID: 456

사용자: "123 포지션에 입사지원해줘"
AI:     이력서가 필요합니다. 새로 만들까요?
사용자: "응 대충 만들어줘"
AI:     ✅ 이력서 생성 (ID: 789)
        ✅ 포지션 123에 입사지원 완료 (지원 ID: 1011)

사용자: "방금 한 걸 레시피로 저장해줘"
AI:     ✅ "입사지원 풀세트" 레시피로 저장 완료
```

---

## 기여 (Contributing)

이슈와 PR은 환영합니다. 기여 전 아래 문서를 참고해주세요:

- `.kiro/steering/` — 프로젝트 규칙 및 컨벤션
- `docs/design/README.md` — 디자인 명세 가이드
- `docs/test/README.md` — QA 테스트 가이드

---

## 라이선스

[LICENSE](./LICENSE) 파일을 참고하세요.
