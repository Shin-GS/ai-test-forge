import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import ChatPage from '@/pages/ChatPage'
import SubdomainPage from '@/pages/SubdomainPage'
import RecipePage from '@/pages/RecipePage'
import SettingsPage from '@/pages/SettingsPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<ChatPage />} />
        <Route path="/subdomains" element={<SubdomainPage />} />
        <Route path="/recipes" element={<RecipePage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
