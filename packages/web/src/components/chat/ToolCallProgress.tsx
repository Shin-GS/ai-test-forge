import { useState } from 'react'
import type { ToolCallItem } from '@/types/chat'

interface ToolCallProgressProps {
  toolCalls: ToolCallItem[]
  isLoading?: boolean
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

interface ToolCallDetailProps {
  toolCall: ToolCallItem
}

function ToolCallDetail({ toolCall }: ToolCallDetailProps) {
  const [isExpanded, setIsExpanded] = useState(false)

  const hasDetail = toolCall.arguments || toolCall.responseBody

  if (!hasDetail) return null

  return (
    <div className="mt-1 ml-6">
      <button
        type="button"
        onClick={() => setIsExpanded(!isExpanded)}
        className="text-xs text-[var(--color-accent)] hover:text-[var(--color-accent-hover)] transition-colors cursor-pointer"
      >
        {isExpanded ? '[상세 접기 ▴]' : '[상세 보기 ▾]'}
      </button>

      {isExpanded && (
        <div className="mt-2 flex flex-col gap-2 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3">
          {toolCall.arguments && (
            <div>
              <div className="mb-1 text-xs font-medium text-[var(--color-text-secondary)]">
                요청 (Arguments)
              </div>
              <pre className="overflow-x-auto rounded-md bg-[var(--color-bg-primary)] p-2 text-xs text-[var(--color-text-primary)] font-[var(--font-mono)]">
                {toolCall.arguments}
              </pre>
            </div>
          )}

          {toolCall.responseBody && (
            <div>
              <div className="mb-1 text-xs font-medium text-[var(--color-text-secondary)]">
                응답 (Result)
              </div>
              <pre className="overflow-x-auto rounded-md bg-[var(--color-bg-primary)] p-2 text-xs text-[var(--color-text-primary)] font-[var(--font-mono)] max-h-48 overflow-y-auto">
                {formatResponseBody(toolCall.responseBody)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function formatResponseBody(body: string): string {
  try {
    const parsed = JSON.parse(body)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return body
  }
}

function ToolCallProgress({ toolCalls, isLoading = true }: ToolCallProgressProps) {
  if (toolCalls.length === 0) return null

  const completedCount = toolCalls.filter((tc) => tc.status === 'done').length
  const totalCount = toolCalls.length
  const allDone = !isLoading && completedCount === totalCount && totalCount > 0

  const headerText = allDone
    ? `✅ API 호출 완료 (${completedCount}/${totalCount})`
    : `🔄 API 호출 중... (${completedCount}/${totalCount})`

  return (
    <div className="mt-3 flex flex-col gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] p-3">
      <div className="mb-2 flex items-center gap-2 text-sm font-medium text-[var(--color-text-primary)]">
        {headerText}
      </div>

      {toolCalls.map((tc) => (
        <div key={tc.id}>
          <div
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

          {/* 완료된 tool call에 상세 접기/펼치기 */}
          {tc.status === 'done' && <ToolCallDetail toolCall={tc} />}
        </div>
      ))}
    </div>
  )
}

export default ToolCallProgress
