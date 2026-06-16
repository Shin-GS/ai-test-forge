import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/useAuthStore'
import {
  getWorkspaces,
  createWorkspace,
  deleteWorkspace,
} from '@/services/workspaceApi'
import type { WorkspaceResponse } from '@/types/workspace'

function WorkspaceCard({ workspace }: { workspace: WorkspaceResponse }) {
  const queryClient = useQueryClient()

  const deleteMutation = useMutation({
    mutationFn: () => deleteWorkspace(workspace.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workspaces'] })
    },
  })

  function handleDelete() {
    if (window.confirm(`"${workspace.name}" 워크스페이스를 삭제하시겠습니까?`)) {
      deleteMutation.mutate()
    }
  }

  return (
    <div className="mb-2 rounded-lg bg-[var(--color-bg-tertiary)] p-3">
      <div className="mb-2 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{workspace.name}</span>
          {workspace.isDefault && (
            <span className="rounded bg-[var(--color-accent)] px-1.5 py-0.5 text-xs text-white">
              기본
            </span>
          )}
        </div>
        <button
          type="button"
          disabled={workspace.isDefault || deleteMutation.isPending}
          onClick={handleDelete}
          className="cursor-pointer rounded px-2 py-1 text-xs text-[var(--color-error)] hover:bg-[var(--color-error-subtle)] disabled:cursor-not-allowed disabled:opacity-40"
        >
          {deleteMutation.isPending ? '삭제 중...' : '삭제'}
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
                <td className="py-1.5 text-[var(--color-text-secondary)]">
                  {mapping.environment}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {workspace.mappings.length === 0 && (
        <p className="text-xs text-[var(--color-text-tertiary)]">
          매핑된 서브도메인이 없습니다.
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
          워크스페이스를 불러오는 중...
        </p>
      )}

      {/* 에러 상태 */}
      {isError && (
        <p className="text-sm text-[var(--color-error)]">
          {error instanceof Error ? error.message : '워크스페이스를 불러올 수 없습니다.'}
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
          등록된 워크스페이스가 없습니다.
        </p>
      )}

      {/* 새 워크스페이스 생성 */}
      {isCreating ? (
        <div className="flex items-center gap-2">
          <input
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="워크스페이스 이름"
            autoFocus
            className="flex-1 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] outline-none placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)]"
          />
          <button
            type="button"
            onClick={handleCreate}
            disabled={!newName.trim() || createMutation.isPending}
            className="cursor-pointer rounded-lg bg-[var(--color-accent)] px-3 py-1.5 text-sm text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {createMutation.isPending ? '생성 중...' : '확인'}
          </button>
          <button
            type="button"
            onClick={() => {
              setIsCreating(false)
              setNewName('')
            }}
            className="cursor-pointer rounded-lg px-3 py-1.5 text-sm text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-tertiary)]"
          >
            취소
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setIsCreating(true)}
          className="cursor-pointer rounded-lg border border-[var(--color-border)] px-3 py-1.5 text-sm text-[var(--color-text-secondary)] hover:border-[var(--color-accent)] hover:text-[var(--color-accent)]"
        >
          + 새 워크스페이스
        </button>
      )}

      {/* 생성 에러 */}
      {createMutation.isError && (
        <p className="mt-2 text-xs text-[var(--color-error)]">
          {createMutation.error instanceof Error
            ? createMutation.error.message
            : '생성에 실패했습니다.'}
        </p>
      )}
    </section>
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
