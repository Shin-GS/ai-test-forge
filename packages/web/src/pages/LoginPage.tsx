import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'
import { login as loginApi } from '@/services/authApi'
import { Button, Input } from '@/components/ui'
import { MESSAGES } from '@/constants'

function LoginPage() {
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [showOtp, setShowOtp] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      const response = await loginApi({ email, password })

      if (response.otpRequired) {
        // OTP 필요 — 2단계 입력 폼 표시
        setShowOtp(true)
        setIsLoading(false)
        return
      }

      // 정상 로그인
      useAuthStore.getState().setAuth(response.token!, response.email!, response.name!)
      navigate('/', { replace: true })
    } catch (err) {
      const message = err instanceof Error ? err.message : MESSAGES.auth.loginFailed
      setError(message)
      setPassword('')
    } finally {
      setIsLoading(false)
    }
  }

  const handleOtpSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError(null)
    setIsLoading(true)

    try {
      const res = await fetch('/api/v1/auth/otp/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, code: otpCode }),
      })
      if (!res.ok) {
        const err = await res.json().catch(() => null)
        throw new Error(err?.message ?? '인증 코드가 올바르지 않습니다.')
      }
      const data = await res.json()
      useAuthStore.getState().setAuth(data.token, data.email, data.name)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : '인증에 실패했습니다.')
      setOtpCode('')
    } finally {
      setIsLoading(false)
    }
  }

  // OTP 입력 화면 (Case 3)
  if (showOtp) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg-primary)]">
        <div className="w-full max-w-[380px] rounded-[var(--radius-xl)] border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-8">
          <div className="mb-6 text-center">
            <div className="text-[2rem]">🔐</div>
            <h1 className="mt-2 text-xl font-bold text-[var(--color-text-primary)]">
              2단계 인증
            </h1>
            <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
              인증 앱에서 코드를 확인하세요
            </p>
          </div>

          {error && (
            <div className="mb-4 rounded-[var(--radius-md)] border border-red-500/30 bg-[var(--color-error-subtle)] p-3 text-sm text-[var(--color-error)]">
              {error}
            </div>
          )}

          <form onSubmit={handleOtpSubmit}>
            <div className="mb-4">
              <Input
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={otpCode}
                onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, ''))}
                placeholder="6자리 코드 입력"
                autoFocus
                className="text-center text-lg tracking-widest"
              />
            </div>
            <Button
              type="submit"
              variant="primary"
              size="lg"
              disabled={otpCode.length !== 6 || isLoading}
              className="w-full"
            >
              {isLoading ? '인증 중...' : '인증'}
            </Button>
          </form>
          <Button
            type="button"
            variant="ghost"
            size="lg"
            className="mt-2 w-full"
            onClick={() => {
              setShowOtp(false)
              setOtpCode('')
              setError(null)
            }}
          >
            ← 이전으로
          </Button>
        </div>
      </div>
    )
  }

  // 기본 로그인 폼 (Case 1, 2)
  return (
    <div className="flex min-h-screen items-center justify-center bg-[var(--color-bg-primary)]">
      <div className="w-full max-w-[380px] rounded-[var(--radius-xl)] border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-8">
        <div className="mb-6 text-center">
          <div className="text-[2rem]">🔨</div>
          <h1 className="mt-2 text-xl font-bold text-[var(--color-text-primary)]">
            {MESSAGES.auth.loginTitle}
          </h1>
          <p className="mt-1 text-sm text-[var(--color-text-secondary)]">
            {MESSAGES.auth.loginSubtitle}
          </p>
        </div>

        {error && (
          <div className="mb-4 rounded-[var(--radius-md)] border border-red-500/30 bg-[var(--color-error-subtle)] p-3 text-sm text-[var(--color-error)]">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="mb-4">
            <label htmlFor="email" className="mb-1 block text-sm font-medium text-[var(--color-text-secondary)]">
              {MESSAGES.auth.emailLabel}
            </label>
            <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder={MESSAGES.auth.emailPlaceholder} required autoComplete="email" />
          </div>
          <div className="mb-4">
            <label htmlFor="password" className="mb-1 block text-sm font-medium text-[var(--color-text-secondary)]">
              {MESSAGES.auth.passwordLabel}
            </label>
            <Input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={MESSAGES.auth.passwordPlaceholder} required autoComplete="current-password" />
          </div>
          <Button type="submit" variant="primary" size="lg" disabled={isLoading} className="w-full">
            {isLoading ? MESSAGES.auth.loginLoading : MESSAGES.auth.loginButton}
          </Button>
        </form>
      </div>
    </div>
  )
}

export default LoginPage
