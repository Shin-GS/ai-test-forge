---
inclusion: fileMatch
fileMatchPattern: ["packages/web/**/*.test.*", "packages/web/**/*.spec.*", "packages/web/vitest.config.*"]
---

# FE 테스트 규칙 (Vitest + React Testing Library)

## 도구

| 도구 | 용도 |
|------|------|
| Vitest | 단위/통합 테스트 러너 (Vite 네이티브) |
| React Testing Library | 컴포넌트 테스트 (사용자 관점) |
| @testing-library/user-event | 사용자 인터랙션 시뮬레이션 |

## 파일 위치 & 네이밍
- 소스 파일 옆: `ComponentName.test.tsx`
- hooks: `useHookName.test.ts`
- utils: `utilName.test.ts`

## 기본 구조

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import MessageBubble from "./MessageBubble";

describe("MessageBubble", () => {
  it("메시지 내용을 렌더링한다", () => {
    render(<MessageBubble content="테스트 메시지" role="user" />);
    expect(screen.getByText("테스트 메시지")).toBeInTheDocument();
  });

  it("전송 버튼 클릭 시 onSend를 호출한다", async () => {
    const user = userEvent.setup();
    const onSend = vi.fn();

    render(<ChatInput onSend={onSend} />);
    await user.type(screen.getByRole("textbox"), "안녕하세요");
    await user.click(screen.getByRole("button", { name: /전송/i }));

    expect(onSend).toHaveBeenCalledWith("안녕하세요");
  });
});
```

## 필수 규칙

1. **사용자 관점 테스트** — 구현 디테일(state, ref)이 아닌 렌더링 결과와 인터랙션 테스트
2. **getByRole 우선** — 접근성 기반 쿼리 우선 사용 (getByRole > getByText > getByTestId)
3. **userEvent 사용** — fireEvent 대신 userEvent로 실제 사용자 행동 시뮬레이션
4. **describe/it 구조** — describe로 컴포넌트/함수 그룹핑, it으로 개별 케이스
5. **한국어 테스트 설명** — it("메시지를 렌더링한다", "에러 시 에러 메시지를 표시한다")
6. **불필요한 snapshot 테스트 지양** — 의미 있는 assertion 사용

## 테스트 우선순위
1. 핵심 비즈니스 로직 (채팅 메시지 파싱, 상태 전이)
2. 사용자 인터랙션 (메시지 전송, 서브도메인 선택)
3. 에러/엣지 케이스 (네트워크 에러, 빈 데이터)
4. 조건부 렌더링

## 실행 명령어
```bash
# 전체 테스트
cd packages/web && pnpm exec vitest run

# 특정 파일
cd packages/web && pnpm exec vitest run src/components/chat/MessageBubble.test.tsx
```
