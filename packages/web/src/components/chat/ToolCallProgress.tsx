import type { ToolCallItem } from '@/types/chat'

interface ToolCallProgressProps {
  toolCalls: ToolCallItem[]
}

function getStatusIcon(status: ToolCallItem['status']): string {
  switch (status) {
    case 'done':
      return '✅'
    case 'active':
      return '⏳'
    case 'error':
      return '❌'
    case 'pending':
    default:
      return '⬜'
  }
}

function getStatusClass(status: ToolCallItem['status']): string {
  switch (status) {
    case 'done':
      return 'text-[var(--color-success)]'
    case 'active':
      return 'text-[var(--color-warning)]'
    case 'error':
      return 'text-[var(--color-error)]'
    case 'pending':
    default:
      return 'text-[var(--color-text-tertiary)]'
  }
}

function ToolCallProgress({ toolCalls }: ToolCallProgressProps) {
  if (toolCalls.length === 0) return null

  const completedCount = toolCalls.filter((tc) => tc.status === 'done').length
  const totalCount = toolCalls.length

  return (
    <div className="mt-3 flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] p-3">
      <div className="mb-2 flex items-center gap-2 text-sm font-medium text-[var(--color-text-primary)]">
        🔄 API 호출 중... ({completedCount}/{totalCount})
      </div>

      {toolCalls.map((tc) => (
        <div
          key={tc.id}
          className={`flex items-center gap-2 py-1 text-sm ${getStatusClass(tc.status)}`}
        >
          <span>{getStatusIcon(tc.status)}</span>
          <span>
            {tc.subdomain} {tc.method} {tc.path}
          </span>
          {tc.status === 'done' && tc.result && (
            <span className="text-xs text-[var(--color-text-tertiary)]">
              → {tc.result}
            </span>
          )}
          {tc.status === 'active' && (
            <span className="text-xs text-[var(--color-text-tertiary)]">
              → 처리 중...
            </span>
          )}
        </div>
      ))}
    </div>
  )
}

export default ToolCallProgress
