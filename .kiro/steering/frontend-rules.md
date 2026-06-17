---
inclusion: fileMatch
fileMatchPattern: ["packages/web/**/*.ts", "packages/web/**/*.tsx", "packages/web/**/*.css"]
---

# Frontend 개발 규칙

## 1. 언어
- 에이전트 응답은 한국어로 작성한다.
- 코드(변수명, 함수명, 컴포넌트명, 타입명)는 모두 영어로 작성한다.
- UI 텍스트, 에러 메시지, placeholder는 한국어로 작성한다.
- 코드 주석은 한국어 허용.

## 2. 컴포넌트 규칙

### 함수형 컴포넌트만 사용

**왜**: hooks 기반 상태 관리, 간결한 코드, React 19 최적화 대상.

```tsx
function MessageBubble({ content, role }: MessageBubbleProps) {
  return (
    <div className="rounded-lg p-4">
      <p>{content}</p>
    </div>
  );
}
```

### 컴포넌트 파일 구조
```tsx
// 1. imports (React → 외부 라이브러리 → 내부 모듈 순)
// 2. types (Props interface)
// 3. component (hooks → handlers → render)
// 4. export default
```

### 네이밍 규칙
- 컴포넌트: PascalCase (`MessageBubble.tsx`)
- hooks: camelCase, `use` 접두사 (`useChat.ts`)
- 유틸리티: camelCase (`formatDate.ts`)
- 타입/인터페이스: PascalCase (`ChatResponse`)
- 상수: UPPER_SNAKE_CASE (`MAX_MESSAGE_LENGTH`)

## 3. 스타일링 규칙

### Tailwind CSS 4

**왜**: 유틸리티 퍼스트로 일관된 디자인 시스템, 번들 크기 최적화.

- 유틸리티 클래스 우선 사용
- 반복되는 스타일은 컴포넌트로 추출 (CSS 추출 지양)
- 인라인 스타일 사용 금지

## 4. 상태 관리

| 유형 | 도구 | 용도 |
|------|------|------|
| 로컬 UI | useState | 단순 토글, 입력값 |
| 복잡 로컬 | useReducer | 다단계 폼, 복잡한 상태 전이 |
| 서버 상태 | @tanstack/react-query | API 데이터 캐싱, 로딩/에러 상태, 리페치 |
| 전역 상태 | zustand | 채팅 세션, 인증 정보 등 |
| 실시간 | SSE (EventSource) | Agent Loop 중간 상태 스트리밍 |

## 5. API 호출 규칙

**왜**: 서비스 레이어 분리로 컴포넌트와 API 로직 결합도를 낮추고, 타입 안전성 확보.

```tsx
// services/chatApi.ts
const API_BASE = "/api/v1";

export async function sendMessage(sessionId: string, message: string): Promise<ChatResponse> {
  const res = await fetch(`${API_BASE}/chat/${sessionId}/messages`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  if (!res.ok) throw new Error("메시지 전송 실패");
  return res.json();
}
```

## 6. TypeScript 규칙

- `any` 타입 사용 금지 (불가피한 경우 주석으로 사유 명시)
- 타입 단언(`as`) 최소화, 타입 가드 활용
- Props: 같은 파일 내 interface 정의
- API 응답/공유 타입: `types/` 디렉토리

## 7. 피해야 할 패턴
- `any` 타입 남용
- useEffect 내 직접 API 호출 → 커스텀 hook으로 분리
- props drilling 3단계 이상 → Context 또는 composition 패턴
- index.ts barrel 파일 남용 → 순환 참조 위험
- `console.log` 프로덕션 코드에 남기기
- 인라인 스타일 사용 → Tailwind 사용

## 8. 페이지 구현 워크플로우

새 페이지를 구현하거나 기존 페이지를 수정할 때 반드시 아래 순서를 따른다.

### 참조 순서 (필수)

1. **`docs/design/web/{화면명}.html`** — 디자인 명세 (source of truth)
2. **`docs/design/web/{화면명}.cases.md`** — 기획 명세
3. **`docs/design/shared/tokens.css`** — 디자인 토큰
4. **`docs/design/shared/components.css`** — 공통 컴포넌트 스타일

### 구현 체크리스트

- [ ] HTML 디자인 명세의 모든 케이스를 조건부 렌더링으로 구현했는가?
- [ ] 레이아웃 구조(순서, 간격, 정렬)가 HTML과 일치하는가?
- [ ] 빈 상태(empty state), 에러 상태, 로딩 상태를 모두 처리했는가?
- [ ] cases.md의 인터랙션(버튼 → API 호출)을 모두 구현했는가?

## 9. Lint 규칙

### ESLint 설정 (eslint.config.js)

- **ESLint 9** flat config 방식 사용
- **TypeScript-ESLint**: `@typescript-eslint/recommended` 적용
- **react-hooks**: `exhaustive-deps`, `rules-of-hooks` 활성화
- **react-refresh**: `only-export-components` 경고

### 주요 규칙

| 규칙 | 수준 | 설명 |
|------|------|------|
| `@typescript-eslint/no-unused-vars` | error | 미사용 변수 금지 (`_` 접두사 예외) |
| `@typescript-eslint/no-explicit-any` | warn | `any` 사용 시 경고 |
| `no-console` | warn | `console.warn`, `console.error`만 허용 |
| `react-hooks/exhaustive-deps` | warn | deps 배열 누락 경고 |
| `react-hooks/rules-of-hooks` | error | hooks 사용 규칙 위반 금지 |

### 비활성화된 규칙 (React Compiler 전용)

React Compiler를 사용하지 않으므로 다음 규칙은 비활성화:
- `react-hooks/immutability`
- `react-hooks/preserve-manual-memoization`
- `react-hooks/set-state-in-effect`

### Zustand store + exhaustive-deps 패턴

Zustand의 `useStore` API에서 store 자체를 deps에 넣지 않는 것은 의도적 패턴.
이 경우에만 `// eslint-disable-next-line react-hooks/exhaustive-deps` 사용 허용.

```tsx
// ✅ 허용: Zustand store 참조 (불변 레퍼런스)
const store = useAgentRunnerStore
useEffect(() => {
  store.getState().doSomething()
  // eslint-disable-next-line react-hooks/exhaustive-deps
}, [])
```

### 실행 방법

```bash
# lint 체크
pnpm --filter ai-test-forge-web lint

# 자동 수정
pnpm --filter ai-test-forge-web lint:fix
```
