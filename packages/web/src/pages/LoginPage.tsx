import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { Button, Input } from '@/components/ui'
import { MESSAGES } from '@/constants'

function LoginPage() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      const message =
        err instanceof Error
          ? err.message
          : MESSAGES.auth.loginFailed
      setError(message)
      setPassword('')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg-primary)]">
      <div className="w-full max-w-[380px] rounded-[var(--radius-xl)] border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-8">
        {/* 로고 */}
        <div className="mb-6 text-center">
          <div className="text-[2rem]">🔨</div>
          <h1 className="mt-2 text-xl font-bold text-[var(--color-text-primary)]">
            {MESSAGES.auth.loginTitle}
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            {MESSAGES.auth.loginSubtitle}
          </p>
        </div>

        {/* 에러 메시지 */}
        {error && (
          <div className="mb-4 rounded-[var(--radius-md)] border border-red-500/30 bg-[var(--color-error-subtle)] p-3 text-sm text-[var(--color-error)]">
            {error}
          </div>
        )}

        {/* 로그인 폼 */}
        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label
              htmlFor="email"
              className="mb-1 block text-sm font-medium text-[var(--color-text-secondary)]"
            >
              {MESSAGES.auth.emailLabel}
            </label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={MESSAGES.auth.emailPlaceholder}
              required
              autoComplete="email"
            />
          </div>

          <div className="mb-4">
            <label
              htmlFor="password"
              className="mb-1 block text-sm font-medium text-[var(--color-text-secondary)]"
            >
              {MESSAGES.auth.passwordLabel}
            </label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={MESSAGES.auth.passwordPlaceholder}
              required
              autoComplete="current-password"
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            size="lg"
            disabled={isLoading}
            className="w-full"
          >
            {isLoading ? MESSAGES.auth.loginLoading : MESSAGES.auth.loginButton}
          </Button>
        </form>
      </div>
    </div>
  )
}

export default LoginPage
