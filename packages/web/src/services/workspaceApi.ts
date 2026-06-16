import type {
  WorkspaceResponse,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
} from '@/types/workspace'

const API_BASE = '/api/v1'

function getAuthHeaders(): HeadersInit {
  const token = localStorage.getItem('auth_token')
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export async function getWorkspaces(): Promise<WorkspaceResponse[]> {
  const res = await fetch(`${API_BASE}/workspaces`, {
    method: 'GET',
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? '워크스페이스 목록을 불러올 수 없습니다.')
  }

  return res.json()
}

export async function createWorkspace(
  data: CreateWorkspaceRequest
): Promise<WorkspaceResponse> {
  const res = await fetch(`${API_BASE}/workspaces`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? '워크스페이스 생성에 실패했습니다.')
  }

  return res.json()
}

export async function updateWorkspace(
  id: number,
  data: UpdateWorkspaceRequest
): Promise<WorkspaceResponse> {
  const res = await fetch(`${API_BASE}/workspaces/${id}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? '워크스페이스 수정에 실패했습니다.')
  }

  return res.json()
}

export async function deleteWorkspace(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/workspaces/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    const error = await res.json().catch(() => null)
    throw new Error(error?.message ?? '워크스페이스 삭제에 실패했습니다.')
  }
}
