import { useAuthStore } from '@/stores/useAuthStore'

function AppHeader() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

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
      <div className="text-sm font-semibold text-[var(--color-text-primary)]">
        🔨 AI Test Forge
      </div>

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
