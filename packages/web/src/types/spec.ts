export type SpecStatus = 'ACTIVE' | 'STALE' | 'REGISTERING'

export interface SpecResponse {
  id: number
  name: string
  environment: string
  baseUrl: string
  status: SpecStatus
  description: string | null
  registeredAt: string
  lastHeartbeatAt: string
}
