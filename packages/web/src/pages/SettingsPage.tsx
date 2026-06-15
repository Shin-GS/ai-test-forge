import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/useAuthStore'

function SettingsPage() {
  const navigate = useNavigate()
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div className="flex-1 overflow-y-auto p-6">
      <div className="mx-auto max-w-[700px]">
        {/* 계정 정보 섹션 */}
        <section className="mb-8">
          <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
            👤 계정
          </h2>

          {/* 이메일 */}
          <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
            <div>
              <div className="text-sm font-medium">이메일</div>
            </div>
            <div className="text-sm text-[var(--color-text-secondary)]">
              {user?.email ?? '-'}
            </div>
          </div>

          {/* 이름 */}
          <div className="flex items-center justify-between py-3">
            <div>
              <div className="text-sm font-medium">이름</div>
            </div>
            <div className="text-sm text-[var(--color-text-secondary)]">
              {user?.name ?? '-'}
            </div>
          </div>

          {/* 로그아웃 */}
          <div className="mt-8 border-t border-[var(--color-border)] pt-4">
            <button
              type="button"
              className="cursor-pointer rounded-lg px-4 py-2 text-sm text-[var(--color-error)] hover:bg-[var(--color-error-subtle)]"
              onClick={handleLogout}
            >
              로그아웃
            </button>
          </div>
        </section>

        {/* AI 설정 섹션 */}
        <section className="mb-8">
          <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
            🤖 AI 설정
          </h2>

          <div className="flex items-center justify-between py-3">
            <div>
              <div className="text-sm font-medium">Provider</div>
              <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
                AI 모델 제공자
              </div>
            </div>
            <div className="text-sm text-[var(--color-text-secondary)]">
              mock
            </div>
          </div>
        </section>

        {/* Agent Loop 설정 섹션 */}
        <section className="mb-8">
          <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
            🔄 Agent Loop 설정
          </h2>

          <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
            <div>
              <div className="text-sm font-medium">최대 반복 횟수</div>
              <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
                AI가 한 번의 요청에서 최대 몇 회까지 반복할지
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-[var(--color-text-secondary)]">
                20
              </span>
              <span className="text-xs text-[var(--color-text-tertiary)]">
                회
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
            <div>
              <div className="text-sm font-medium">턴당 Tool Call</div>
              <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
                한 번의 AI 응답에서 최대 API 호출 수
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-[var(--color-text-secondary)]">
                5
              </span>
              <span className="text-xs text-[var(--color-text-tertiary)]">
                개
              </span>
            </div>
          </div>

          <div className="flex items-center justify-between py-3">
            <div>
              <div className="text-sm font-medium">타임아웃</div>
              <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
                전체 Agent Loop의 최대 실행 시간
              </div>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-sm text-[var(--color-text-secondary)]">
                120
              </span>
              <span className="text-xs text-[var(--color-text-tertiary)]">
                초
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

export default SettingsPage
