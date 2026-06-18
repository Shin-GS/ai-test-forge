import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useWorkspaceStore } from './useWorkspaceStore'
import type { WorkspaceResponse } from '@/types/workspace'

// workspaceApi 모듈 mock
vi.mock('@/services/workspaceApi', () => ({
  getWorkspaces: vi.fn(),
}))

import { getWorkspaces } from '@/services/workspaceApi'

const mockWorkspaces: WorkspaceResponse[] = [
  {
    id: 1,
    name: '개발 환경',
    isDefault: true,
    mappings: [{ subdomainName: 'user-service', environment: 'dev' }],
    createdAt: '2024-01-01T00:00:00',
  },
  {
    id: 2,
    name: '로그인 리팩토링',
    isDefault: false,
    mappings: [
      { subdomainName: 'user-service', environment: 'feature-login' },
      { subdomainName: 'auth-service', environment: 'feature-login' },
    ],
    createdAt: '2024-01-02T00:00:00',
  },
]

describe('useWorkspaceStore', () => {
  beforeEach(() => {
    // store 초기화
    useWorkspaceStore.setState({
      workspaces: [],
      activeWorkspaceId: null,
      isLoading: false,
    })
    // localStorage mock
    vi.stubGlobal('localStorage', {
      getItem: vi.fn(() => null),
      setItem: vi.fn(),
      removeItem: vi.fn(),
      clear: vi.fn(),
      length: 0,
      key: vi.fn(),
    })
    vi.mocked(getWorkspaces).mockReset()
  })

  describe('초기 상태', () => {
    it('workspaces가 빈 배열이다', () => {
      expect(useWorkspaceStore.getState().workspaces).toEqual([])
    })

    it('activeWorkspaceId가 null이다', () => {
      expect(useWorkspaceStore.getState().activeWorkspaceId).toBeNull()
    })

    it('isLoading이 false이다', () => {
      expect(useWorkspaceStore.getState().isLoading).toBe(false)
    })
  })

  describe('fetchWorkspaces', () => {
    it('워크스페이스 목록을 가져와서 상태에 저장한다', async () => {
      vi.mocked(getWorkspaces).mockResolvedValue(mockWorkspaces)

      await useWorkspaceStore.getState().fetchWorkspaces()

      const state = useWorkspaceStore.getState()
      expect(state.workspaces).toEqual(mockWorkspaces)
      expect(state.isLoading).toBe(false)
    })

    it('기본 워크스페이스를 activeWorkspaceId로 설정한다', async () => {
      vi.mocked(getWorkspaces).mockResolvedValue(mockWorkspaces)

      await useWorkspaceStore.getState().fetchWorkspaces()

      expect(useWorkspaceStore.getState().activeWorkspaceId).toBe(1)
    })

    it('localStorage에 저장된 ID가 있으면 해당 워크스페이스를 활성화한다', async () => {
      vi.mocked(localStorage.getItem).mockReturnValue('2')
      vi.mocked(getWorkspaces).mockResolvedValue(mockWorkspaces)

      await useWorkspaceStore.getState().fetchWorkspaces()

      expect(useWorkspaceStore.getState().activeWorkspaceId).toBe(2)
    })

    it('localStorage에 존재하지 않는 ID가 있으면 기본 워크스페이스를 활성화한다', async () => {
      vi.mocked(localStorage.getItem).mockReturnValue('999')
      vi.mocked(getWorkspaces).mockResolvedValue(mockWorkspaces)

      await useWorkspaceStore.getState().fetchWorkspaces()

      expect(useWorkspaceStore.getState().activeWorkspaceId).toBe(1)
    })

    it('API 호출 실패 시 isLoading을 false로 복구한다', async () => {
      vi.mocked(getWorkspaces).mockRejectedValue(new Error('네트워크 에러'))

      await useWorkspaceStore.getState().fetchWorkspaces()

      const state = useWorkspaceStore.getState()
      expect(state.isLoading).toBe(false)
      expect(state.workspaces).toEqual([])
    })
  })

  describe('setActiveWorkspace', () => {
    it('activeWorkspaceId를 변경한다', () => {
      useWorkspaceStore.getState().setActiveWorkspace(2)

      expect(useWorkspaceStore.getState().activeWorkspaceId).toBe(2)
    })

    it('localStorage에 선택된 워크스페이스 ID를 저장한다', () => {
      useWorkspaceStore.getState().setActiveWorkspace(2)

      expect(localStorage.setItem).toHaveBeenCalledWith('active_workspace_id', '2')
    })

    it('다른 워크스페이스로 전환할 수 있다', () => {
      useWorkspaceStore.getState().setActiveWorkspace(1)
      useWorkspaceStore.getState().setActiveWorkspace(2)

      expect(useWorkspaceStore.getState().activeWorkspaceId).toBe(2)
    })
  })
})
