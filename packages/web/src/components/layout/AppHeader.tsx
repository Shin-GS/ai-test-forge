import { useState, useEffect, useRef } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { useWorkspaceStore } from '@/stores/useWorkspaceStore'

function AppHeader() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  const workspaces = useWorkspaceStore((s) => s.workspaces)
  const activeWorkspaceId = useWorkspaceStore((s) => s.activeWorkspaceId)
  const fetchWorkspaces = useWorkspaceStore((s) => s.fetchWorkspaces)
  const setActiveWorkspace = useWorkspaceStore((s) => s.setActiveWorkspace)

  const [isDropdownOpen, setIsDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    fetchWorkspaces()
  }, [fetchWorkspaces])

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setIsDropdownOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const activeWorkspace = workspaces.find((ws) => ws.id === activeWorkspaceId)

  const initials = user?.name
    ? user.name
        .split(' ')
        .map((n) => n[0])
        .join('')
        .toUpperCase()
        .slice(0, 2)
    : '??'

  return (
    <header className="flex h-14 shrink-0 items-center justify-between border-b border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-4">
      {/* 좌: 로고 */}
      <div className="text-sm font-semibold text-[var(--color-text-primary)]">
        🔨 AI Test Forge
      </div>

      {/* 중: 워크스페이스 드롭다운 */}
      {workspaces.length > 0 && (
        <div className="relative" ref={dropdownRef}>
          <button
            type="button"
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-[var(--color-text-secondary)] transition-colors hover:bg-[var(--color-bg-hover)] hover:text-[var(--color-text-primary)]"
          >
            <span>🌐</span>
            <span>{activeWorkspace?.name ?? '워크스페이스'}</span>
            <span className="text-xs">▾</span>
          </button>

          {isDropdownOpen && (
            <div className="absolute left-1/2 top-full z-50 mt-1 w-56 -translate-x-1/2 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] py-1 shadow-lg">
              {workspaces.map((ws) => (
                <button
                  key={ws.id}
                  type="button"
                  onClick={() => {
                    setActiveWorkspace(ws.id)
                    setIsDropdownOpen(false)
                  }}
                  className={`flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition-colors ${
                    ws.id === activeWorkspaceId
                      ? 'bg-[var(--color-accent-subtle)] text-[var(--color-accent)]'
                      : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-hover)] hover:text-[var(--color-text-primary)]'
                  }`}
                >
                  <span>{ws.id === activeWorkspaceId ? '◉' : '○'}</span>
                  <span>{ws.name}</span>
                  {ws.isDefault && (
                    <span className="ml-auto text-xs text-[var(--color-text-tertiary)]">
                      기본
                    </span>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {/* 우: 유저 아바타 + 로그아웃 */}
      <div className="flex items-center gap-3">
        <div className="flex h-8 w-8 items-center justify-center rounded-full bg-[var(--color-accent)] text-xs font-medium text-white">
          {initials}
        </div>
        <button
          onClick={logout}
          className="text-xs text-[var(--color-text-tertiary)] transition-colors hover:text-[var(--color-text-primary)]"
        >
          로그아웃
        </button>
      </div>
    </header>
  )
}

export default AppHeader
