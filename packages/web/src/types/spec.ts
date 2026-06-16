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

export interface ApiEndpointResponse {
  method: string
  path: string
  summary: string
  tag: string
}

export interface SpecDetailResponse {
  id: number
  name: string
  environment: string
  baseUrl: string
  status: SpecStatus
  description: string | null
  apiCount: number
  endpoints: ApiEndpointResponse[]
  registeredAt: string
  lastHeartbeatAt: string
}
