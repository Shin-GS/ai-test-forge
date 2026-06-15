import type { SessionResponse } from '@/types/chat'

interface SessionSidebarProps {
  sessions: SessionResponse[]
  activeSessionId: number | null
  onSelectSession: (sessionId: number) => void
  onNewChat: () => void
}

function formatSessionDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffDays === 0) return '오늘'
  if (diffDays === 1) return '어제'
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function truncateTitle(title: string, maxLength = 20): string {
  if (title.length <= maxLength) return title
  return title.slice(0, maxLength) + '...'
}

function SessionSidebar({
  sessions,
  activeSessionId,
  onSelectSession,
  onNewChat,
}: SessionSidebarProps) {
  return (
    <aside className="flex w-60 shrink-0 flex-col overflow-y-auto border-r border-[var(--color-border)] bg-[var(--color-bg-secondary)]">
      {/* 헤더 */}
      <div className="px-4 py-3 text-xs font-medium text-[var(--color-text-tertiary)]">
        채팅 세션
      </div>

      {/* 세션 목록 */}
      <div className="flex-1 overflow-y-auto">
        {sessions.map((session) => (
          <button
            key={session.id}
            onClick={() => onSelectSession(session.id)}
            className={`flex w-full items-center gap-2 px-4 py-2 text-left text-sm transition-colors ${
              session.id === activeSessionId
                ? 'bg-[var(--color-accent-subtle)] text-[var(--color-accent)]'
                : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-hover)] hover:text-[var(--color-text-primary)]'
            }`}
          >
            {session.id === activeSessionId && <span>◉</span>}
            <span className="truncate">
              {formatSessionDate(session.createdAt)} — {truncateTitle(session.title)}
            </span>
          </button>
        ))}
      </div>

      {/* 새 대화 버튼 */}
      <div className="mt-auto border-t border-[var(--color-border)] px-4 py-3">
        <button
          onClick={onNewChat}
          className="w-full rounded-lg bg-transparent px-3 py-2 text-sm text-[var(--color-text-secondary)] transition-colors hover:bg-[var(--color-bg-hover)] hover:text-[var(--color-text-primary)]"
        >
          + 새 대화
        </button>
      </div>
    </aside>
  )
}

export default SessionSidebar
