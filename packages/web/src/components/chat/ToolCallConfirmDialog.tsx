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
      <div className="mx-4 w-full max-w-md rounded-lg bg-white shadow-xl">
        {/* 헤더 */}
        <div className="border-b border-gray-200 px-6 py-4">
          <h2
            id="confirm-dialog-title"
            className="text-lg font-semibold text-gray-900"
          >
            API 실행 확인
          </h2>
        </div>

        {/* 본문 */}
        <div className="space-y-4 px-6 py-4">
          {/* confirm 메시지 */}
          {toolCall.control?.confirmMessage && (
            <div className="rounded-md bg-amber-50 p-3">
              <p className="text-sm text-amber-800">
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
              <span className="truncate text-sm font-mono text-gray-700">
                {toolCall.path}
              </span>
            </div>

            <div className="flex items-center gap-2 text-sm text-gray-500">
              <span className="rounded bg-gray-100 px-2 py-0.5 text-xs">
                {toolCall.subdomain}
              </span>
              <span>{toolCall.name}</span>
            </div>
          </div>

          {/* arguments 미리보기 */}
          {toolCall.arguments && (
            <details className="group">
              <summary className="cursor-pointer text-sm text-gray-500 hover:text-gray-700">
                요청 본문 보기
              </summary>
              <pre className="mt-2 max-h-40 overflow-auto rounded bg-gray-50 p-3 text-xs text-gray-600">
                {formatArguments(toolCall.arguments)}
              </pre>
            </details>
          )}
        </div>

        {/* 버튼 */}
        <div className="flex justify-end gap-3 border-t border-gray-200 px-6 py-4">
          <button
            type="button"
            onClick={onReject}
            className="rounded-md border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-400 focus:ring-offset-2"
          >
            건너뛰기
          </button>
          <button
            type="button"
            onClick={onConfirm}
            className="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"
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
      return 'bg-green-600'
    case 'POST':
      return 'bg-blue-600'
    case 'PUT':
      return 'bg-orange-500'
    case 'PATCH':
      return 'bg-yellow-600'
    case 'DELETE':
      return 'bg-red-600'
    default:
      return 'bg-gray-600'
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
