<div align="center">

# 🔨 AI Test Forge

**마이크로서비스 테스트 데이터, 채팅 한 줄로 끝.**

여러 서비스에 걸친 테스트 데이터를 자연어 대화로 생성하는 AI 기반 플랫폼

</div>

---

## 💡 왜 필요한가?

### 현재의 고통

마이크로서비스 환경에서 **"입사지원"** 하나 테스트하려면:

```
1. 회원서비스 → Postman에서 회원 생성 API 호출
2. 이력서서비스 → 이력서 생성 (회원 ID 복붙)
3. 채용서비스 → 포지션 확인
4. 채용서비스 → 입사지원 API 호출 (이력서 ID, 포지션 ID 복붙)
```

> ⏱️ 단순 반복 작업에 **10~15분**, 서비스 간 의존성 파악에 **추가 시간**

이걸 매일, 매주, 환경 초기화할 때마다 반복합니다.

### AI Test Forge를 쓰면

```
사용자: "입사지원 테스트 데이터 만들어줘"
AI:     ✅ 완료 (30초)
        - 회원 생성 (ID: 456)
        - 이력서 생성 (ID: 789)
        - 포지션 123에 입사지원 완료
```

---

## 📊 도입 효과

| 지표 | Before | After | 개선 |
|------|--------|-------|------|
| 단일 시나리오 세팅 시간 | 10~15분 | 30초~1분 | **90% 단축** |
| 신규 팀원 온보딩 | API 의존성 학습 필요 | 채팅으로 바로 사용 | **즉시 생산성** |
| 환경 초기화 후 재세팅 | 30분~1시간 | 레시피 실행 3분 | **95% 단축** |
| QA 테스트 데이터 구성 | 개발자 요청 대기 | QA가 직접 생성 | **병목 제거** |

### 월간 추정 절감 (개발자 10명 팀 기준)

```
하루 평균 테스트 데이터 세팅: 3회 × 15분 = 45분/인
월간: 45분 × 20일 × 10명 = 150시간/월

AI Test Forge 도입 후: 150시간 × 90% 절감 = 135시간/월 절약
→ 약 3.4인월의 생산성 확보
```

---

## 🎯 핵심 기능

| 기능 | 설명 |
|------|------|
| 🗣️ **채팅 기반 생성** | 자연어 한 마디로 멀티 서비스 API 자동 호출 |
| 🔄 **AI Agent Loop** | 서비스 간 의존성을 AI가 파악, 순서대로 자동 실행 |
| 📋 **레시피** | 자주 쓰는 패턴 저장 → 원클릭 재실행 (AI 비용 0) |
| 🔌 **자동 서버 등록** | 라이브러리 추가만으로 API 스펙 자동 연동 + heartbeat |
| 🌐 **워크스페이스** | 작업할 서버 세트를 구성 (K8s feature 환경 대응) |
| 🔐 **브라우저 인증** | 기존 로그인 그대로 사용 (SSO 자동 지원) |
| 🤖 **AI 교체** | OpenAI, Claude 등 인터페이스 기반 자유 교체 |

---

## 🏗️ 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    브라우저 (FE)                          │
│  ┌─────────┐    ┌──────────────┐    ┌───────────────┐  │
│  │ 채팅 UI │◄──►│ AI Test Forge│◄──►│ 서브도메인 API │  │
│  │         │SSE │   메인 서버   │    │  직접 호출     │  │
│  └─────────┘    └──────────────┘    └───────────────┘  │
└─────────────────────────────────────────────────────────┘
                         │
          AI 판단 (BE)   │   실제 실행 (FE, 쿠키 자동 부착)
                         │
┌────────────────────────┼────────────────────────────────┐
│ 서브도메인 서버들        │                                │
│  ┌──────────┐  ┌──────┴─────┐  ┌──────────────┐       │
│  │user-svc  │  │recruit-svc │  │payment-svc   │  ...  │
│  │(OpenAPI) │  │(OpenAPI)   │  │(OpenAPI)     │       │
│  └──────────┘  └────────────┘  └──────────────┘       │
│       │ push + heartbeat                                │
│       └──────────────► AI Test Forge 메인 서버           │
└─────────────────────────────────────────────────────────┘
```

**핵심 구조:**
- AI 판단은 메인 서버(BE)에서 수행
- 실제 API 호출은 브라우저(FE)가 직접 실행 → **쿠키/세션 자동 부착**
- 서브도메인 서버에 별도 수정 불필요 (CORS 허용만 추가)

---

## 🚀 빠른 시작

### 사전 요구사항

- Java 25+ / Node.js 22+ & pnpm / MySQL 8.x
- (선택) OpenAI API Key 또는 Claude API Key

### 설치 & 실행

```bash
# 1. 클론 + 환경 설정
git clone https://github.com/your-org/ai-test-forge.git
cd ai-test-forge
cp .env.example .env          # DB, AI API 키 설정

# 2. DB 생성
mysql -e "CREATE DATABASE ai_test_forge CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 3. 서버 실행 (루트에서)
./gradlew :packages:server:bootRun

# 4. UI 실행 (별도 터미널)
cd packages/web && pnpm install && pnpm dev

# 5. 초기 계정 생성 (서버 실행 후)
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"admin1234","name":"관리자"}'
```

브라우저에서 `http://localhost:5173` 접속 → 위 계정으로 로그인 → 채팅 시작!

---

## 🔌 서브도메인 서버 연동

### Spring Boot (클라이언트 라이브러리)

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.aitestforge:client-spring:0.1.0")
}
```

```yaml
# application.yml
ai-test-forge:
  enabled: true
  server-url: http://test-forge.company.com
  subdomain-name: user-service
  base-url: http://user.dev.company.com
  profiles: dev, qa
  auth:
    profiles:
      - name: 개인회원
        login-page-url: https://user.dev.company.com/login
      - name: 어드민
        login-page-url: https://user.dev.company.com/admin/login
```

### 비-Java 서버 (Node.js, PHP, Python 등)

클라이언트 라이브러리 없이 API 직접 호출:

```bash
curl -X POST http://test-forge.company.com/api/v1/specs/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "payment-service",
    "environment": "dev",
    "baseUrl": "http://payment.dev.company.com",
    "specJson": "<OpenAPI JSON 내용>"
  }'
```

**전제**: 서브도메인 서버가 OpenAPI JSON을 생성할 수 있어야 함 (대부분의 프레임워크에서 라이브러리로 지원).

---

## 📋 레시피 (반복 패턴 저장)

```
사용자: "방금 한 작업을 레시피로 저장해줘"
AI:     ✅ "입사지원 풀세트" 저장 완료 (3단계)

사용자: "입사지원 레시피 실행해줘"
AI:     포지션 ID를 알려주세요.
사용자: "123"
AI:     ✅ 30초 만에 완료 (AI 호출 없이 직접 실행 — 비용 0)
```

| 방식 | AI 토큰 비용 | 속도 |
|------|-------------|------|
| 처음 대화 (AI가 판단) | 높음 | 보통 |
| **레시피 실행** | **거의 0** | **빠름** |

---

## 🌐 워크스페이스 (대규모 환경 대응)

서버가 30개, K8s feature 환경까지 있을 때:

```
"로그인 리팩토링" 워크스페이스
  - user-service → feature-login
  - auth-service → feature-login  
  - payment-service → dev          (feature 없으니 dev 사용)
```

워크스페이스 한 번 구성해두면, 채팅할 때 환경 지정 없이 바로 사용.

---

## 🛠️ 기술 스택

| 구성요소 | 기술 |
|---------|------|
| 메인 서버 | Java 25 / Spring Boot 4.0.x / Gradle |
| 웹 UI | React 19 / TypeScript / Vite 6 / Tailwind CSS 4 |
| 실시간 | SSE (서버→FE) + REST (FE→서버) |
| 클라이언트 라이브러리 | Spring Boot Starter (Java 17+) |
| DB | MySQL 8.x |
| AI | OpenAI, Claude, OpenRouter (인터페이스 기반 교체) |

---

## 📁 프로젝트 구조

```
ai-test-forge/
├── packages/
│   ├── server/              # Spring Boot 메인 서버
│   ├── web/                 # React 채팅 UI
│   └── client-spring/       # Spring Boot Starter (서브도메인용)
├── docs/
│   ├── design/              # 디자인 명세
│   └── test/                # QA 테스트 체크리스트
└── .kiro/                   # AI 개발 도구 설정
```

---

## ⚙️ 설계 원칙

| 원칙 | 설명 |
|------|------|
| FE 직접 호출 | 브라우저가 API 호출 → 쿠키/SSO 자동, credential 저장 불필요 |
| AI 모델 비강제 | 인터페이스 기반으로 자유 교체 |
| 프로필 기반 제어 | dev/qa에서만 동작, prod 자동 비활성화 |
| Swagger 비의존 | HTTP로 raw JSON만 전달 (버전 호환 문제 회피) |
| 언어 무관 | OpenAPI JSON만 제공하면 어떤 언어든 연동 가능 |
| 텍스트 분리 | UI 텍스트를 상수로 관리, fork 시 언어 교체 용이 |

---

## 🤝 기여

이슈와 PR은 환영합니다.

- `.kiro/steering/` — 프로젝트 규칙
- `docs/design/README.md` — 디자인 가이드
- `docs/test/README.md` — QA 가이드

---

## 📄 라이선스

[MIT License](LICENSE)
