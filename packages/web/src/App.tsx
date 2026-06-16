import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import AppLayout from '@/components/layout/AppLayout'
import ChatPage from '@/pages/ChatPage'
import SubdomainPage from '@/pages/SubdomainPage'
import SubdomainDetailPage from '@/pages/SubdomainDetailPage'
import RecipePage from '@/pages/RecipePage'
import SettingsPage from '@/pages/SettingsPage'
import LoginPage from '@/pages/LoginPage'
import { useEffect } from 'react'

interface ProtectedRouteProps {
  children: React.ReactNode
}

function ProtectedRoute({ children }: ProtectedRouteProps) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

function App() {
  const hydrate = useAuthStore((s) => s.hydrate)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)

  useEffect(() => {
    hydrate()
  }, [hydrate])

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/login"
          element={
            isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />
          }
        />
        <Route
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route path="/" element={<ChatPage />} />
          <Route path="/subdomains" element={<SubdomainPage />} />
          <Route path="/subdomains/:name" element={<SubdomainDetailPage />} />
          <Route path="/recipes" element={<RecipePage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
