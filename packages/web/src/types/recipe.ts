export interface RecipeResponse {
  id: number
  name: string
  description: string
  tags: string[]
  stepsJson: string
  visibility: 'PUBLIC' | 'PRIVATE'
  variablesJson: string | null
  validationStatus: 'VALID' | 'WARN' | 'BROKEN' | null
  validationMessage: string | null
  usageCount: number
  createdAt: string
  lastUsedAt: string | null
}

export interface RecipeStep {
  subdomain: string
  api: string
  params: Record<string, string>
  output?: string
}

export interface UpdateRecipeRequest {
  name: string
  description: string
  tags: string[]
  stepsJson: string
  visibility: 'PUBLIC' | 'PRIVATE'
  variablesJson: string | null
}
