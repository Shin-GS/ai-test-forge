import { Outlet } from 'react-router-dom'
import AppHeader from '@/components/layout/AppHeader'
import TabNav from '@/components/layout/TabNav'

function AppLayout() {
  return (
    <div className="flex h-screen flex-col bg-[var(--color-bg-primary)]">
      <AppHeader />
      <TabNav />
      <main className="flex flex-1 overflow-hidden">
        <Outlet />
      </main>
    </div>
  )
}

export default AppLayout
