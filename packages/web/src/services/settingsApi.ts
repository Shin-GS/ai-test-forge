const API_BASE = '/api/v1'

export interface SettingsResponse {
  aiProvider: string
  aiModel: string
  maxIterations: number
  maxToolCallsPerTurn: number
  timeoutSeconds: number
  nextActionHintEnabled: boolean
}

export interface UpdateSettingsRequest {
  aiProvider: string
  aiModel: string
  maxIterations: number
  maxToolCallsPerTurn: number
  timeoutSeconds: number
  nextActionHintEnabled: boolean
}

export async function getSettings(): Promise<SettingsResponse> {
  const res = await fetch(`${API_BASE}/settings`, {
    headers: { 'Content-Type': 'application/json' },
  })
  if (!res.ok) throw new Error('설정을 불러올 수 없습니다.')
  return res.json()
}

export async function updateSettings(
  request: UpdateSettingsRequest,
): Promise<SettingsResponse> {
  const res = await fetch(`${API_BASE}/settings`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  if (!res.ok) throw new Error('설정 저장에 실패했습니다.')
  return res.json()
}
