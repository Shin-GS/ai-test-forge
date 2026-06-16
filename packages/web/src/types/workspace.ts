export interface WorkspaceMappingDto {
  subdomainName: string
  environment: string
}

export interface WorkspaceResponse {
  id: number
  name: string
  isDefault: boolean
  mappings: WorkspaceMappingDto[]
  createdAt: string
}

export interface CreateWorkspaceRequest {
  name: string
  mappings?: WorkspaceMappingDto[]
}

export interface UpdateWorkspaceRequest {
  name?: string
  mappings?: WorkspaceMappingDto[]
}
