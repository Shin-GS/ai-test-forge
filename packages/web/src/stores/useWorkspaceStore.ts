import { create } from 'zustand'
import type { WorkspaceResponse } from '@/types/workspace'
import { getWorkspaces } from '@/services/workspaceApi'

interface WorkspaceState {
  workspaces: WorkspaceResponse[]
  activeWorkspaceId: number | null
  isLoading: boolean
  fetchWorkspaces: () => Promise<void>
  setActiveWorkspace: (id: number) => void
}

const ACTIVE_WORKSPACE_KEY = 'active_workspace_id'

export const useWorkspaceStore = create<WorkspaceState>((set) => ({
  workspaces: [],
  activeWorkspaceId: null,
  isLoading: false,

  fetchWorkspaces: async () => {
    set({ isLoading: true })
    try {
      const workspaces = await getWorkspaces()
      const savedId = localStorage.getItem(ACTIVE_WORKSPACE_KEY)
      let activeId: number | null = null

      if (savedId && workspaces.some((ws) => ws.id === Number(savedId))) {
        activeId = Number(savedId)
      } else {
        // 기본 워크스페이스 또는 첫 번째 워크스페이스
        const defaultWs = workspaces.find((ws) => ws.isDefault)
        activeId = defaultWs?.id ?? workspaces[0]?.id ?? null
      }

      set({ workspaces, activeWorkspaceId: activeId, isLoading: false })
    } catch {
      set({ isLoading: false })
    }
  },

  setActiveWorkspace: (id: number) => {
    localStorage.setItem(ACTIVE_WORKSPACE_KEY, String(id))
    set({ activeWorkspaceId: id })
  },
}))
