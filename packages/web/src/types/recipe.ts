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

/** Body 생성 전략 */
export type BodyStrategy = 'fixed' | 'gen' | 'ai-generate' | 'ai-fill'

/** 조회 결과 선택 전략 */
export type SelectStrategy = 'ai-pick'

/**
 * 레시피 스텝 (BE JSON 스키마 기준)
 * - 구버전 호환: `api`, `params`, `output` 필드가 있는 데이터도 파싱 가능
 */
export interface RecipeStep {
  name: string
  subdomain: string
  environment: string
  method: string
  path: string
  bodyStrategy: BodyStrategy
  body: Record<string, unknown> | null
  aiHint: string | null
  selectStrategy: SelectStrategy | null
  extract: Record<string, string> | null

  // 구버전 호환 (legacy)
  /** @deprecated "POST /api/members" 형태. 신규 데이터에서는 method + path 사용 */
  api?: string
  /** @deprecated body를 flat하게 표현. 신규 데이터에서는 body 사용 */
  params?: Record<string, string>
  /** @deprecated extract로 대체 */
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

export interface RecipeValidationResult {
  status: 'VALID' | 'WARN' | 'BROKEN'
  issues: RecipeValidationIssue[]
}

export interface RecipeValidationIssue {
  stepIndex: number
  type: string
  message: string
}
