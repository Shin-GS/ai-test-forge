export type SpecStatus = 'ACTIVE' | 'STALE' | 'REGISTERING'

export interface SpecResponse {
  id: number
  name: string
  environment: string
  baseUrl: string
  status: SpecStatus
  description: string | null
  authProfiles: AuthProfileDto[]
  registeredAt: string
  lastHeartbeatAt: string
}

export interface AuthProfileDto {
  name: string
  loginPageUrl: string
}

export interface ApiEndpointResponse {
  method: string
  path: string
  summary: string
  tag: string
}

export interface ExcludedApiResponse {
  method: string
  path: string
  reason: string
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
  authProfiles: AuthProfileDto[]
  excludedApis: ExcludedApiResponse[]
  registeredAt: string
  lastHeartbeatAt: string
}
