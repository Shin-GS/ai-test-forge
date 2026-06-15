import { NavLink } from 'react-router-dom'

interface TabItem {
  to: string
  icon: string
  label: string
}

const TABS: TabItem[] = [
  { to: '/', icon: '💬', label: '채팅' },
  { to: '/subdomains', icon: '📡', label: '서브도메인' },
  { to: '/recipes', icon: '📋', label: '레시피' },
  { to: '/settings', icon: '⚙️', label: '설정' },
]

function TabNav() {
  return (
    <nav className="flex h-11 shrink-0 items-center gap-1 border-b border-[var(--color-border)] bg-[var(--color-bg-primary)] px-4">
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.to === '/'}
          className={({ isActive }) =>
            `flex items-center gap-1.5 rounded-[var(--radius-md)] px-3 py-1.5 text-sm font-medium transition-colors ${
              isActive
                ? 'bg-[var(--color-accent-subtle)] text-[var(--color-accent)]'
                : 'text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-hover)] hover:text-[var(--color-text-primary)]'
            }`
          }
        >
          <span>{tab.icon}</span>
          <span>{tab.label}</span>
        </NavLink>
      ))}
    </nav>
  )
}

export default TabNav
