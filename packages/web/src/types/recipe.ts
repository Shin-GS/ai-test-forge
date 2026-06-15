export interface RecipeResponse {
  id: number
  name: string
  description: string
  tags: string[]
  stepsJson: string
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
