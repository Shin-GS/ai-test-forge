interface OnboardingProps {
  onQuickAction: (message: string) => void
}

interface QuickActionItem {
  icon: string
  label: string
  message: string
}

const QUICK_ACTIONS: QuickActionItem[] = [
  { icon: '👤', label: '개인회원 생성', message: '개인회원 테스트 데이터 만들어줘' },
  { icon: '📋', label: '입사지원 데이터', message: '입사지원 테스트 데이터 만들어줘' },
  { icon: '💳', label: '결제 테스트', message: '결제 테스트 데이터 만들어줘' },
  { icon: '🔑', label: '어드민 계정 생성', message: '어드민 계정 테스트 데이터 만들어줘' },
]

function Onboarding({ onQuickAction }: OnboardingProps) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-6 text-center">
      <h2 className="text-xl font-semibold text-[var(--color-text-primary)]">
        🚀 빠른 시작
      </h2>

      <div className="flex flex-wrap justify-center gap-3">
        {QUICK_ACTIONS.map((action) => (
          <button
            key={action.label}
            onClick={() => onQuickAction(action.message)}
            className="flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-4 py-2 text-sm text-[var(--color-text-primary)] transition-colors hover:border-[var(--color-accent)] hover:bg-[var(--color-accent-subtle)]"
          >
            <span>{action.icon}</span>
            <span>{action.label}</span>
          </button>
        ))}
      </div>

      <p className="text-sm text-[var(--color-text-tertiary)]">
        또는 자유롭게 입력하세요...
      </p>
    </div>
  )
}

export default Onboarding
