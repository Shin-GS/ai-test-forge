import type { ToolCallEvent } from '@/types/agentRunner'

interface ToolCallConfirmDialogProps {
  toolCall: ToolCallEvent
  onConfirm: () => void
  onReject: () => void
}

/**
 * tool_call confirm 모달.
 * API 메서드, 경로, confirmMessage를 표시하고 [실행] [건너뛰기] 선택.
 */
function ToolCallConfirmDialog({
  toolCall,
  onConfirm,
  onReject,
}: ToolCallConfirmDialogProps) {
  const methodColorClass = getMethodColorClass(toolCall.method)

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
    >
      <div className="mx-4 w-full max-w-md rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] shadow-[var(--shadow-lg)]">
        {/* 헤더 */}
        <div className="border-b border-[var(--color-border)] px-6 py-4">
          <h2
            id="confirm-dialog-title"
            className="text-lg font-semibold text-[var(--color-text-primary)]"
          >
            API 실행 확인
          </h2>
        </div>

        {/* 본문 */}
        <div className="space-y-4 px-6 py-4">
          {/* confirm 메시지 */}
          {toolCall.control?.confirmMessage && (
            <div className="rounded-md bg-[var(--color-warning-subtle)] p-3">
              <p className="text-sm text-[var(--color-warning)]">
                {toolCall.control.confirmMessage}
              </p>
            </div>
          )}

          {/* API 정보 */}
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <span
                className={`rounded px-2 py-0.5 text-xs font-bold uppercase text-white ${methodColorClass}`}
              >
                {toolCall.method}
              </span>
              <span className="truncate text-sm font-mono text-[var(--color-text-secondary)]">
                {toolCall.path}
              </span>
            </div>

            <div className="flex items-center gap-2 text-sm text-[var(--color-text-tertiary)]">
              <span className="rounded bg-[var(--color-bg-tertiary)] px-2 py-0.5 text-xs">
                {toolCall.subdomain}
              </span>
              <span>{toolCall.name}</span>
            </div>
          </div>

          {/* arguments 미리보기 */}
          {toolCall.arguments && (
            <details className="group">
              <summary className="cursor-pointer text-sm text-[var(--color-text-tertiary)] hover:text-[var(--color-text-secondary)]">
                요청 본문 보기
              </summary>
              <pre className="mt-2 max-h-40 overflow-auto rounded bg-[var(--color-bg-tertiary)] p-3 text-xs text-[var(--color-text-secondary)] font-[var(--font-mono)]">
                {formatArguments(toolCall.arguments)}
              </pre>
            </details>
          )}
        </div>

        {/* 버튼 */}
        <div className="flex justify-end gap-3 border-t border-[var(--color-border)] px-6 py-4">
          <button
            type="button"
            onClick={onReject}
            className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-4 py-2 text-sm font-medium text-[var(--color-text-primary)] transition-colors hover:bg-[var(--color-bg-hover)]"
          >
            건너뛰기
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-[var(--radius-md)] bg-[var(--color-accent)] px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)]"
          >
            실행
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * HTTP 메서드별 배경색 클래스.
 */
function getMethodColorClass(method: string): string {
  switch (method.toUpperCase()) {
    case 'GET':
      return 'bg-[var(--color-success)]'
    case 'POST':
      return 'bg-[var(--color-info)]'
    case 'PUT':
      return 'bg-[var(--color-warning)]'
    case 'PATCH':
      return 'bg-[var(--color-warning)]'
    case 'DELETE':
      return 'bg-[var(--color-error)]'
    default:
      return 'bg-[var(--color-text-tertiary)]'
  }
}

/**
 * JSON arguments를 포맷팅.
 */
function formatArguments(args: string): string {
  try {
    const parsed = JSON.parse(args)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return args
  }
}

export default ToolCallConfirmDialog
