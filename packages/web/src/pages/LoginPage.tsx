import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'

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
          : '이메일 또는 비밀번호가 올바르지 않습니다.'
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
            AI Test Forge
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            테스트 데이터 생성 플랫폼
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
              이메일
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@company.com"
              required
              autoComplete="email"
              className="w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-tertiary)] outline-none transition-[border-color] duration-[var(--transition-fast)] focus:border-[var(--color-accent)]"
            />
          </div>

          <div className="mb-4">
            <label
              htmlFor="password"
              className="mb-1 block text-sm font-medium text-[var(--color-text-secondary)]"
            >
              비밀번호
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호 입력"
              required
              autoComplete="current-password"
              className="w-full rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder-[var(--color-text-tertiary)] outline-none transition-[border-color] duration-[var(--transition-fast)] focus:border-[var(--color-accent)]"
            />
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full rounded-[var(--radius-md)] bg-[var(--color-accent)] px-4 py-3 text-sm font-medium text-white transition-[background-color] duration-[var(--transition-fast)] hover:bg-[var(--color-accent-hover)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isLoading ? '로그인 중...' : '로그인'}
          </button>
        </form>
      </div>
    </div>
  )
}

export default LoginPage
