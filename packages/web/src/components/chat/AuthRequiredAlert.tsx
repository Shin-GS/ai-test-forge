import { useCallback } from 'react'
import type { PauseData } from '@/types/agentRunner'

interface AuthRequiredAlertProps {
  pauseData: PauseData | null
  onResume: () => void
}

function AuthRequiredAlert({ pauseData, onResume }: AuthRequiredAlertProps) {
  const subdomainName = pauseData?.subdomainName ?? '서브도메인'
  const loginUrl = pauseData?.loginUrl

  const handleLoginClick = useCallback(() => {
    if (loginUrl) {
      window.open(loginUrl, '_blank', 'noopener,noreferrer')
    }
  }, [loginUrl])

  return (
    <div className="mx-auto mt-4 max-w-[800px]">
      <div className="rounded-lg border border-[var(--color-warning)] bg-[var(--color-warning-subtle)] px-4 py-3">
        <div className="text-sm font-semibold text-[var(--color-text-primary)]">
          ⚠️ {subdomainName}에 로그인이 필요합니다.
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          {loginUrl && (
            <button
              onClick={handleLoginClick}
              className="inline-flex items-center gap-1 rounded-md border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm font-medium text-[var(--color-text-primary)] transition-colors hover:bg-[var(--color-bg-tertiary)]"
            >
              🔗 웹에서 로그인 →
            </button>
          )}
        </div>
        <div className="mt-3">
          <button
            onClick={onResume}
            className="inline-flex items-center rounded-md bg-[var(--color-accent)] px-3 py-1.5 text-sm font-medium text-white transition-colors hover:bg-[var(--color-accent-hover)]"
          >
            로그인 완료 — 계속 진행
          </button>
        </div>
      </div>
    </div>
  )
}

export default AuthRequiredAlert
