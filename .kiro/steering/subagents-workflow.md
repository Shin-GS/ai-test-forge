---
inclusion: always
---

# 서브에이전트 작업 위임 규칙

## 코드 수정 시 서브에이전트 활용 원칙

코드 수정이 필요한 작업에서는 서브에이전트가 직접 코드를 수정하도록 위임한다.
main 에이전트는 분석/조율/결과 보고만 담당한다.

## 에이전트 목록

| 에이전트 | 역할 | 산출물 |
|----------|------|--------|
| `product-planner` | 기획 문서 관리, 케이스 정의 | .cases.md, business-logic.md, glossary.md |
| `ui-designer` | 디자인 명세 구현, 디자인 시스템 유지보수 | HTML/CSS/JS (docs/design/) |
| `frontend-developer` | FE 코드 구현 | React 컴포넌트 (packages/web/) |
| `backend-developer` | BE 코드 구현 | Java 코드 (packages/server/, packages/client-spring/) |
| `frontend-code-reviewer` | FE 코드 리뷰 | 피드백 |
| `backend-code-reviewer` | BE 코드 리뷰 | 피드백 |
| `java-architect` | BE 아키텍처 검증 | 피드백 |
| `qa-tester` | 수동 테스트 체크리스트 생성 및 관리 | JS 데이터 + 매니페스트 (docs/test/) |

## 워크플로우: 기능 추가/변경

기능 추가 또는 기획 변경 요청 시 아래 순서를 따른다.

### Phase 1: 기획

1. `product-planner` 호출:
   - 기존 문서(business-logic.md, product.md, glossary.md) 확인
   - cases.md 작성/수정
   - 필요 시 glossary.md, business-logic.md 업데이트

### Phase 2: 디자인

2. `ui-designer` 호출 (기획자 산출물 전달):
   - cases.md 기반으로 HTML/CSS/JS 구현
   - 필요 시 tokens.css, components.css 수정

### Phase 3: FE/BE 구현 (필요 시)

3. 서브에이전트 호출 (병렬 가능):
   - `frontend-developer`: 디자인 명세 기반 React 구현
   - `backend-developer`: API 구현

### Phase 4: 리뷰

4. 코드 리뷰 서브에이전트 호출 (병렬 가능):
   - `frontend-code-reviewer`: FE 변경 코드 리뷰
   - `backend-code-reviewer`: BE 변경 코드 리뷰
5. main 에이전트: 리뷰 피드백 평가 후 수정 필요 여부 판단
6. 아키텍처 이슈 검증 (필요 시): `java-architect`

### Phase 5: 보고

7. main 에이전트: 최종 결과 확인 후 사용자에게 변경 내용 보고

## 워크플로우: QA 체크리스트

테스트 체크리스트 생성/업데이트 요청 시:

1. `qa-tester` 직접 호출
2. 기능 추가/변경 워크플로우 완료 후 자동 호출 (Phase 5 보고 전에 실행)

## 워크플로우: 디자인만 변경

1. `ui-designer` 직접 호출
2. 토큰 변경이 있으면 → `frontend-developer`에게 FE 동기화 지시

## 워크플로우: 기획만 변경

1. `product-planner` 직접 호출
2. cases.md 변경으로 HTML 업데이트 필요 시 → `ui-designer` 연쇄 호출

## 서브에이전트 호출 시 주의사항

- 프롬프트에 "직접 파일을 수정해줘"를 명시할 것
- 변경 대상 파일 경로와 현재 코드 컨텍스트를 충분히 제공할 것
- FE/BE 간 인터페이스(API 스펙, DTO 구조)가 변경되는 경우 양쪽에 동일한 스펙을 전달할 것
- 리뷰어는 코드 수정을 직접 하지 않고, 피드백만 반환할 것
- 피드백 기반 수정 후 재리뷰는 하지 않는다 (1회 리뷰 원칙)

## main 에이전트가 직접 수정해도 되는 경우
- steering/spec 등 설정 파일 수정
- 단일 파일의 간단한 수정 (한두 줄 변경)
- glossary.md, README.md 등 문서 단순 업데이트

## Git 커밋 규칙

- `git add -A` 또는 `git add .` 사용 금지
- 반드시 변경 대상 파일을 명시적으로 `git add <파일1> <파일2> ...`로 스테이징
- 커밋 전 `git diff --cached --stat`으로 스테이징 파일 확인
- untracked 파일은 사용자가 명시적으로 요청한 경우에만 커밋에 포함
