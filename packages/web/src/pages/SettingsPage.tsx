import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import {
  getWorkspaces,
  createWorkspace,
  deleteWorkspace,
  updateWorkspace,
} from '@/services/workspaceApi'
import { getSettings, updateSettings } from '@/services/settingsApi'
import { changePassword, getMe, setupOtp, verifyOtp, disableOtp } from '@/services/authApi'
import type { WorkspaceResponse } from '@/types/workspace'
import { Button, Input } from '@/components/ui'
import { MESSAGES } from '@/constants'

function WorkspaceCard({ workspace }: { workspace: WorkspaceResponse }) {
  const queryClient = useQueryClient()

  const deleteMutation = useMutation({
    mutationFn: () => deleteWorkspace(workspace.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: (mappings: { subdomainName: string; environment: string }[]) =>
      updateWorkspace(workspace.id, { mappings }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
    },
  })

  function handleDelete() {
    if (window.confirm(MESSAGES.settings.workspace.confirmDelete(workspace.name))) {
      deleteMutation.mutate()
    }
  }

  function handleEnvironmentChange(subdomainName: string, newEnv: string) {
    const updatedMappings = workspace.mappings.map((m) =>
      m.subdomainName === subdomainName ? { ...m, environment: newEnv } : m,
    )
    updateMutation.mutate(updatedMappings)
  }

  return (
    <div className="mb-2 rounded-lg bg-[var(--color-bg-tertiary)] p-3">
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{workspace.name}</span>
          {workspace.isDefault && (
            <span className="rounded bg-[var(--color-accent)] px-1.5 py-0.5 text-xs text-white">
              {MESSAGES.settings.workspace.default}
            </span>
          )}
        </div>
        <button
          type="button"
          disabled={workspace.isDefault || deleteMutation.isPending}
          onClick={handleDelete}
          className="cursor-pointer rounded px-2 py-1 text-xs text-[var(--color-error)] hover:bg-[var(--color-error-subtle)] disabled:cursor-not-allowed disabled:opacity-40"
        >
          {deleteMutation.isPending ? MESSAGES.settings.workspace.deleting : MESSAGES.common.delete}
        </button>
      </div>

      {workspace.mappings.length > 0 && (
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-[var(--color-border)]">
              <th className="py-1 text-left text-xs font-medium text-[var(--color-text-tertiary)]">
                서브도메인
              </th>
              <th className="py-1 text-left text-xs font-medium text-[var(--color-text-tertiary)]">
                환경
              </th>
            </tr>
          </thead>
          <tbody>
            {workspace.mappings.map((mapping) => (
              <tr
                key={mapping.subdomainName}
                className="border-b border-[var(--color-border)] last:border-b-0"
              >
                <td className="py-1.5 text-[var(--color-text-primary)]">
                  {mapping.subdomainName}
                </td>
                <td className="py-1.5">
                  <input
                    type="text"
                    defaultValue={mapping.environment}
                    onBlur={(e) => {
                      const newVal = e.target.value.trim()
                      if (newVal && newVal !== mapping.environment) {
                        handleEnvironmentChange(mapping.subdomainName, newVal)
                      }
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') (e.target as HTMLInputElement).blur()
                    }}
                    className="w-full rounded border border-transparent bg-transparent px-1 py-0.5 text-sm text-[var(--color-text-secondary)] outline-none hover:border-[var(--color-border)] focus:border-[var(--color-accent)] focus:bg-[var(--color-bg-tertiary)]"
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {workspace.mappings.length === 0 && (
        <p className="text-xs text-[var(--color-text-tertiary)]">
          {MESSAGES.settings.workspace.emptyMappings}
        </p>
      )}
    </div>
  )
}

function WorkspaceSection() {
  const queryClient = useQueryClient()
  const [isCreating, setIsCreating] = useState(false)
  const [newName, setNewName] = useState('')

  const { data: workspaces, isLoading, isError, error } = useQuery({
    queryKey: ['workspaces'],
    queryFn: getWorkspaces,
  })

  const createMutation = useMutation({
    mutationFn: createWorkspace,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
      setIsCreating(false)
      setNewName('')
    },
  })

  function handleCreate() {
    const trimmed = newName.trim()
    if (!trimmed) return
    createMutation.mutate({ name: trimmed })
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      handleCreate()
    } else if (e.key === 'Escape') {
      setIsCreating(false)
      setNewName('')
    }
  }

  return (
    <section className="mb-8">
      <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
        🌐 워크스페이스
      </h2>

      {/* 로딩 상태 */}
      {isLoading && (
        <p className="text-sm text-[var(--color-text-tertiary)]">
          {MESSAGES.settings.workspace.loading}
        </p>
      )}

      {/* 에러 상태 */}
      {isError && (
        <p className="text-sm text-[var(--color-error)]">
          {error instanceof Error ? error.message : MESSAGES.settings.workspace.loadError}
        </p>
      )}

      {/* 워크스페이스 목록 */}
      {workspaces && workspaces.length > 0 && (
        <div className="mb-3">
          {workspaces.map((ws) => (
            <WorkspaceCard key={ws.id} workspace={ws} />
          ))}
        </div>
      )}

      {/* 빈 상태 */}
      {workspaces && workspaces.length === 0 && (
        <p className="mb-3 text-sm text-[var(--color-text-tertiary)]">
          {MESSAGES.settings.workspace.empty}
        </p>
      )}

      {/* 새 워크스페이스 생성 */}
      {isCreating ? (
        <div className="flex items-center gap-2">
          <Input
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={MESSAGES.settings.workspace.namePlaceholder}
            autoFocus
            className="flex-1"
          />
          <Button
            variant="primary"
            size="sm"
            onClick={handleCreate}
            disabled={!newName.trim() || createMutation.isPending}
          >
            {createMutation.isPending ? MESSAGES.settings.workspace.creating : MESSAGES.common.confirm}
          </Button>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setIsCreating(false)
              setNewName('')
            }}
          >
            {MESSAGES.common.cancel}
          </Button>
        </div>
      ) : (
        <Button
          variant="secondary"
          size="sm"
          onClick={() => setIsCreating(true)}
        >
          {MESSAGES.settings.workspace.createButton}
        </Button>
      )}

      {/* 생성 에러 */}
      {createMutation.isError && (
        <p className="mt-2 text-xs text-[var(--color-error)]">
          {createMutation.error instanceof Error
            ? createMutation.error.message
            : MESSAGES.settings.workspace.createFailed}
        </p>
      )}
    </section>
  )
}

function AiSettingsSection() {
  const queryClient = useQueryClient()

  const { data: settings, isLoading, isError, error } = useQuery({
    queryKey: ['settings'],
    queryFn: getSettings,
  })

  const updateMutation = useMutation({
    mutationFn: updateSettings,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] })
    },
  })

  if (isLoading) {
    return (
      <section className="mb-8">
        <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
          🤖 {MESSAGES.settings.ai.title}
        </h2>
        <p className="text-sm text-[var(--color-text-tertiary)]">
          {MESSAGES.settings.ai.loading}
        </p>
      </section>
    )
  }

  if (isError) {
    return (
      <section className="mb-8">
        <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
          🤖 {MESSAGES.settings.ai.title}
        </h2>
        <p className="text-sm text-[var(--color-error)]">
          {error instanceof Error ? error.message : MESSAGES.settings.ai.loadError}
        </p>
      </section>
    )
  }

  return (
    <section className="mb-8">
      <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
        🤖 {MESSAGES.settings.ai.title}
      </h2>

      {/* Reasoning 모델 (읽기 전용) */}
      <h3 className="mt-4 mb-2 text-sm font-medium">{MESSAGES.settings.ai.reasoningTitle}</h3>
      <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
        <div className="text-sm font-medium">{MESSAGES.settings.ai.providerLabel}</div>
        <span className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)] opacity-70">
          {settings?.reasoningProvider}
        </span>
      </div>
      <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
        <div className="text-sm font-medium">{MESSAGES.settings.ai.modelLabel}</div>
        <span className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)] opacity-70">
          {settings?.reasoningModel}
        </span>
      </div>

      {/* Fast 모델 (읽기 전용) */}
      <h3 className="mt-4 mb-2 text-sm font-medium">{MESSAGES.settings.ai.fastTitle}</h3>
      <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
        <div className="text-sm font-medium">{MESSAGES.settings.ai.providerLabel}</div>
        <span className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)] opacity-70">
          {settings?.fastProvider}
        </span>
      </div>
      <div className="flex items-center justify-between py-3">
        <div className="text-sm font-medium">{MESSAGES.settings.ai.modelLabel}</div>
        <span className="rounded-[var(--radius-md)] border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)] opacity-70">
          {settings?.fastModel}
        </span>
      </div>

      {/* 안내 문구 */}
      <p className="mt-2 text-xs text-[var(--color-text-tertiary)]">
        {MESSAGES.settings.ai.readonlyNotice}
      </p>

      {/* 다음 액션 힌트 토글 */}
      <div className="mt-4 flex items-center justify-between border-t border-[var(--color-border)] py-3">
        <div>
          <div className="text-sm font-medium">{MESSAGES.settings.ai.nextActionLabel}</div>
          <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
            {MESSAGES.settings.ai.nextActionDescription}
          </div>
        </div>
        <button
          type="button"
          onClick={() => {
            if (!settings) return
            updateMutation.mutate({
              maxIterations: settings.maxIterations,
              maxToolCallsPerTurn: settings.maxToolCallsPerTurn,
              timeoutSeconds: settings.timeoutSeconds,
              nextActionHintEnabled: !settings.nextActionHintEnabled,
            })
          }}
          className={`relative h-6 w-11 rounded-full transition-colors ${
            settings?.nextActionHintEnabled
              ? 'bg-[var(--color-accent)]'
              : 'bg-[var(--color-bg-tertiary)] border border-[var(--color-border)]'
          }`}
          role="switch"
          aria-checked={settings?.nextActionHintEnabled ?? false}
        >
          <span
            className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
              settings?.nextActionHintEnabled ? 'translate-x-5' : 'translate-x-0'
            }`}
          />
        </button>
      </div>

      {/* 저장 상태 표시 */}
      {updateMutation.isPending && (
        <p className="mt-1 text-xs text-[var(--color-text-tertiary)]">
          {MESSAGES.common.loading}
        </p>
      )}
      {updateMutation.isError && (
        <p className="mt-1 text-xs text-[var(--color-error)]">
          {MESSAGES.settings.agentLoop.saveFailed}
        </p>
      )}
    </section>
  )
}

function AgentLoopSection() {
  const queryClient = useQueryClient()

  const { data: settings, isLoading, isError, error } = useQuery({
    queryKey: ['settings'],
    queryFn: getSettings,
  })

  const [maxIterations, setMaxIterations] = useState(20)
  const [maxToolCalls, setMaxToolCalls] = useState(5)
  const [timeout, setTimeout] = useState(120)
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saved' | 'failed'>('idle')

  useEffect(() => {
    if (settings) {
      setMaxIterations(settings.maxIterations)
      setMaxToolCalls(settings.maxToolCallsPerTurn)
      setTimeout(settings.timeoutSeconds)
    }
  }, [settings])

  const updateMutation = useMutation({
    mutationFn: updateSettings,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings'] })
      setSaveStatus('saved')
      window.setTimeout(() => setSaveStatus('idle'), 2000)
    },
    onError: () => {
      setSaveStatus('failed')
      window.setTimeout(() => setSaveStatus('idle'), 2000)
    },
  })

  function handleSave() {
    if (!settings) return
    updateMutation.mutate({
      maxIterations,
      maxToolCallsPerTurn: maxToolCalls,
      timeoutSeconds: timeout,
      nextActionHintEnabled: settings.nextActionHintEnabled,
    })
  }

  function clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max)
  }

  if (isLoading) {
    return (
      <section className="mb-8">
        <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
          🔄 {MESSAGES.settings.agentLoop.title}
        </h2>
        <p className="text-sm text-[var(--color-text-tertiary)]">
          {MESSAGES.settings.ai.loading}
        </p>
      </section>
    )
  }

  if (isError) {
    return (
      <section className="mb-8">
        <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
          🔄 {MESSAGES.settings.agentLoop.title}
        </h2>
        <p className="text-sm text-[var(--color-error)]">
          {error instanceof Error ? error.message : MESSAGES.settings.ai.loadError}
        </p>
      </section>
    )
  }

  return (
    <section className="mb-8">
      <h2 className="mb-4 border-b border-[var(--color-border)] pb-2 text-lg font-semibold">
        🔄 {MESSAGES.settings.agentLoop.title}
      </h2>

      {/* 최대 반복 횟수 */}
      <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
        <div>
          <div className="text-sm font-medium">{MESSAGES.settings.agentLoop.maxIterationsLabel}</div>
        </div>
        <div className="flex items-center gap-2">
          <Input
            type="number"
            min={1}
            max={100}
            value={maxIterations}
            onChange={(e) => setMaxIterations(clamp(Number(e.target.value), 1, 100))}
            className="w-20 text-center"
          />
          <span className="text-xs text-[var(--color-text-tertiary)]">
            {MESSAGES.settings.agentLoop.maxIterationsUnit}
          </span>
        </div>
      </div>

      {/* 턴당 Tool Call */}
      <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
        <div>
          <div className="text-sm font-medium">{MESSAGES.settings.agentLoop.maxToolCallsLabel}</div>
        </div>
        <div className="flex items-center gap-2">
          <Input
            type="number"
            min={1}
            max={20}
            value={maxToolCalls}
            onChange={(e) => setMaxToolCalls(clamp(Number(e.target.value), 1, 20))}
            className="w-20 text-center"
          />
          <span className="text-xs text-[var(--color-text-tertiary)]">
            {MESSAGES.settings.agentLoop.maxToolCallsUnit}
          </span>
        </div>
      </div>

      {/* 타임아웃 */}
      <div className="flex items-center justify-between border-b border-[var(--color-border)] py-3">
        <div>
          <div className="text-sm font-medium">{MESSAGES.settings.agentLoop.timeoutLabel}</div>
        </div>
        <div className="flex items-center gap-2">
          <Input
            type="number"
            min={10}
            max={600}
            value={timeout}
            onChange={(e) => setTimeout(clamp(Number(e.target.value), 10, 600))}
            className="w-20 text-center"
          />
          <span className="text-xs text-[var(--color-text-tertiary)]">
            {MESSAGES.settings.agentLoop.timeoutUnit}
          </span>
        </div>
      </div>

      {/* 저장 버튼 */}
      <div className="mt-4 flex items-center gap-3">
        <Button
          variant="primary"
          size="sm"
          onClick={handleSave}
          disabled={updateMutation.isPending}
        >
          {saveStatus === 'saved'
            ? MESSAGES.settings.agentLoop.saved
            : saveStatus === 'failed'
              ? MESSAGES.settings.agentLoop.saveFailed
              : MESSAGES.settings.agentLoop.saveButton}
        </Button>
      </div>
    </section>
  )
}

function ChangePasswordForm() {
  const [isOpen, setIsOpen] = useState(false)
  const [currentPw, setCurrentPw] = useState('')
  const [newPw, setNewPw] = useState('')
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const mutation = useMutation({
    mutationFn: () => changePassword({ currentPassword: currentPw, newPassword: newPw }),
    onSuccess: () => {
      setMessage({ type: 'success', text: '비밀번호가 변경되었습니다.' })
      setCurrentPw('')
      setNewPw('')
      window.setTimeout(() => {
        setIsOpen(false)
        setMessage(null)
      }, 2000)
    },
    onError: (err: Error) => {
      setMessage({ type: 'error', text: err.message })
    },
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setMessage(null)
    mutation.mutate()
  }

  if (!isOpen) {
    return (
      <div className="flex items-center justify-between border-t border-[var(--color-border)] py-3">
        <div className="text-sm font-medium">비밀번호</div>
        <Button variant="secondary" size="sm" onClick={() => setIsOpen(true)}>
          비밀번호 변경
        </Button>
      </div>
    )
  }

  return (
    <div className="border-t border-[var(--color-border)] py-3">
      <div className="mb-2 text-sm font-medium">비밀번호 변경</div>
      <form onSubmit={handleSubmit} className="flex flex-col gap-2">
        <Input
          type="password"
          placeholder="현재 비밀번호"
          value={currentPw}
          onChange={(e) => setCurrentPw(e.target.value)}
          required
        />
        <Input
          type="password"
          placeholder="새 비밀번호"
          value={newPw}
          onChange={(e) => setNewPw(e.target.value)}
          required
        />
        {message && (
          <p className={`text-xs ${message.type === 'success' ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
            {message.text}
          </p>
        )}
        <div className="flex gap-2">
          <Button variant="primary" size="sm" type="submit" disabled={!currentPw || !newPw || mutation.isPending}>
            {mutation.isPending ? '변경 중...' : '변경'}
          </Button>
          <Button variant="ghost" size="sm" onClick={() => { setIsOpen(false); setMessage(null) }}>
            {MESSAGES.common.cancel}
          </Button>
        </div>
      </form>
    </div>
  )
}

function OtpSection() {
  const queryClient = useQueryClient()
  const [otpUri, setOtpUri] = useState('')
  const [otpCode, setOtpCode] = useState('')
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const { data: me, isLoading } = useQuery({
    queryKey: ['auth-me'],
    queryFn: getMe,
  })

  const setupMutation = useMutation({
    mutationFn: setupOtp,
    onSuccess: (data) => {
      setOtpUri(data.otpAuthUri)
      setMessage(null)
    },
    onError: (err: Error) => {
      setMessage({ type: 'error', text: err.message })
    },
  })

  const verifyMutation = useMutation({
    mutationFn: verifyOtp,
    onSuccess: () => {
      setMessage({ type: 'success', text: 'OTP 인증이 활성화되었습니다.' })
      setOtpUri('')
      setOtpCode('')
      queryClient.invalidateQueries({ queryKey: ['auth-me'] })
    },
    onError: (err: Error) => {
      setMessage({ type: 'error', text: err.message })
    },
  })

  const disableMutation = useMutation({
    mutationFn: disableOtp,
    onSuccess: () => {
      setMessage({ type: 'success', text: 'OTP 인증이 비활성화되었습니다.' })
      setOtpUri('')
      setOtpCode('')
      queryClient.invalidateQueries({ queryKey: ['auth-me'] })
    },
    onError: (err: Error) => {
      setMessage({ type: 'error', text: err.message })
    },
  })

  const otpEnabled = me?.otpEnabled ?? false
  const isSettingUp = !!otpUri

  function handleToggle() {
    setMessage(null)
    if (otpEnabled) {
      disableMutation.mutate()
    } else {
      setupMutation.mutate()
    }
  }

  function handleVerify(e: React.FormEvent) {
    e.preventDefault()
    if (!otpCode.trim()) return
    verifyMutation.mutate(otpCode.trim())
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-between border-t border-[var(--color-border)] py-3">
        <div className="text-sm font-medium">OTP 2단계 인증</div>
        <span className="text-xs text-[var(--color-text-tertiary)]">로딩 중...</span>
      </div>
    )
  }

  return (
    <div className="border-t border-[var(--color-border)] py-3">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-sm font-medium">OTP 2단계 인증</div>
          <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
            TOTP 기반 2단계 인증을 활성화합니다.
          </div>
        </div>
        <button
          type="button"
          onClick={handleToggle}
          disabled={setupMutation.isPending || disableMutation.isPending || isSettingUp}
          className={`relative h-6 w-11 rounded-full transition-colors disabled:opacity-60 ${
            otpEnabled
              ? 'bg-[var(--color-accent)]'
              : 'bg-[var(--color-bg-tertiary)] border border-[var(--color-border)]'
          }`}
          role="switch"
          aria-checked={otpEnabled}
          aria-label="OTP 2단계 인증 토글"
        >
          <span
            className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
              otpEnabled ? 'translate-x-5' : 'translate-x-0'
            }`}
          />
        </button>
      </div>

      {/* OTP 설정 진행 중 (QR URI + 코드 입력) */}
      {isSettingUp && (
        <div className="mt-3 rounded-lg bg-[var(--color-bg-tertiary)] p-3">
          <div className="mb-2 text-xs font-medium text-[var(--color-text-secondary)]">
            인증 앱에 아래 URI를 등록하세요:
          </div>
          <div className="mb-3 break-all rounded border border-[var(--color-border)] bg-[var(--color-bg-primary)] p-2 text-xs font-mono text-[var(--color-text-primary)]">
            {otpUri}
          </div>
          <form onSubmit={handleVerify} className="flex items-center gap-2">
            <Input
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              placeholder="6자리 인증 코드"
              value={otpCode}
              onChange={(e) => setOtpCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              className="w-36"
              autoFocus
            />
            <Button
              variant="primary"
              size="sm"
              type="submit"
              disabled={otpCode.length !== 6 || verifyMutation.isPending}
            >
              {verifyMutation.isPending ? '인증 중...' : '인증'}
            </Button>
            <Button
              variant="ghost"
              size="sm"
              type="button"
              onClick={() => {
                setOtpUri('')
                setOtpCode('')
                setMessage(null)
              }}
            >
              취소
            </Button>
          </form>
        </div>
      )}

      {/* 메시지 표시 */}
      {message && (
        <p className={`mt-2 text-xs ${message.type === 'success' ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
          {message.text}
        </p>
      )}
    </div>
  )
}

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
        {/* 워크스페이스 섹션 (첫 번째) */}
        <WorkspaceSection />

        {/* AI 설정 섹션 */}
        <AiSettingsSection />

        {/* Agent Loop 설정 섹션 */}
        <AgentLoopSection />

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

          {/* 비밀번호 변경 */}
          <ChangePasswordForm />

          {/* OTP 2단계 인증 */}
          <OtpSection />

          {/* 로그아웃 */}
          <div className="mt-8 border-t border-[var(--color-border)] pt-4">
            <Button
              variant="danger"
              size="md"
              onClick={handleLogout}
            >
              {MESSAGES.auth.logout}
            </Button>
          </div>
        </section>
      </div>
    </div>
  )
}

export default SettingsPage
