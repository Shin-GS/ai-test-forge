# Contributing to AI Test Forge

기여해 주셔서 감사합니다! 이 문서는 프로젝트에 기여하는 방법을 안내합니다.

## 개발 환경 설정

```bash
# 사전 요구사항: Java 25+, Node.js 22+, pnpm, MySQL 8
git clone https://github.com/your-org/ai-test-forge.git
cd ai-test-forge
cp .env.example .env   # DB, JWT 설정

# DB 생성
mysql -e "CREATE DATABASE ai_test_forge CHARACTER SET utf8mb4;"

# 서버 실행
./gradlew :packages:server:bootRun

# 웹 UI (별도 터미널)
cd packages/web && pnpm install && pnpm dev
```

또는 Docker Compose로 한 번에:
```bash
docker compose up -d
```

## 프로젝트 구조

```
packages/server/      # Spring Boot BE
packages/web/         # React FE
packages/client-spring/  # 클라이언트 라이브러리
docs/design/          # 디자인 명세 (HTML/CSS)
docs/test/            # QA 체크리스트
.kiro/steering/       # 프로젝트 규칙
```

## 코드 규칙

### BE (Java + Spring Boot)
- `backend-rules.md` 참조
- Entity: `@Builder` + protected no-arg constructor
- Service: `@Transactional(readOnly = true)` 클래스 레벨
- DTO: Java record
- 테스트: `@ExtendWith(MockitoExtension.class)`, BDDMockito

### FE (React + TypeScript)
- `frontend-rules.md` 참조
- 함수형 컴포넌트, Tailwind CSS 4
- 상태: zustand (전역), react-query (서버)
- 테스트: Vitest + React Testing Library

## 커밋 메시지

```
feat: 새 기능
fix: 버그 수정
refactor: 리팩토링
test: 테스트 추가/수정
docs: 문서 변경
ci: CI 설정
infra: 인프라 (Docker, 빌드 등)
chore: 기타
```

스코프: `feat(web):`, `feat(server):`, `test(server):` 등

## PR 가이드

1. `main`에서 feature 브랜치 생성
2. 변경 사항 구현 + 테스트
3. `pnpm build` + `pnpm test` 통과 확인
4. `./gradlew :packages:server:compileTestJava` 통과 확인
5. PR 생성 (변경 요약 + 테스트 방법 기술)

## 테스트 실행

```bash
# BE 컴파일 체크
./gradlew :packages:server:compileTestJava

# FE 테스트
cd packages/web && pnpm exec vitest run

# FE 빌드
cd packages/web && pnpm build
```

## 이슈 제보

- 버그: 재현 단계, 예상 동작, 실제 동작
- 기능 요청: 사용 시나리오, 기대 동작
