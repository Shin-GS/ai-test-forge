import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getRecipes, deleteRecipe, updateRecipe, cloneRecipe, validateRecipe } from '@/services/recipeApi'
import { useChatStore } from '@/stores/useChatStore'
import RecipeCard from '@/components/recipe/RecipeCard'
import { Button } from '@/components/ui'
import { MESSAGES } from '@/constants'
import type { RecipeResponse, RecipeStep, RecipeValidationResult, UpdateRecipeRequest } from '@/types/recipe'

function RecipePage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedTag, setSelectedTag] = useState('')
  const [selectedRecipeId, setSelectedRecipeId] = useState<number | null>(null)
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const {
    data: recipes = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['recipes'],
    queryFn: getRecipes,
  })

  // 유니크 태그 목록 추출
  const uniqueTags = [...new Set(recipes.flatMap((recipe) => recipe.tags))].sort()

  // 검색 + 태그 필터: 레시피명, 설명, 태그로 필터
  const filteredRecipes = recipes.filter((recipe) => {
    const query = searchQuery.toLowerCase()
    const matchesSearch =
      recipe.name.toLowerCase().includes(query) ||
      recipe.description.toLowerCase().includes(query) ||
      recipe.tags.some((tag) => tag.toLowerCase().includes(query))
    const matchesTag = selectedTag === '' || recipe.tags.includes(selectedTag)
    return matchesSearch && matchesTag
  })

  // "자주 사용" 섹션: usageCount > 0인 레시피, 사용 횟수 순 정렬
  const frequentRecipes = [...filteredRecipes]
    .filter((r) => r.usageCount > 0)
    .sort((a, b) => b.usageCount - a.usageCount)

  // 상세 보기 대상 레시피
  const selectedRecipe = selectedRecipeId
    ? recipes.find((r) => r.id === selectedRecipeId) ?? null
    : null

  // 삭제 mutation
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteRecipe(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      setSelectedRecipeId(null)
    },
  })

  const handleDetailOpen = (recipeId: number) => {
    setSelectedRecipeId(recipeId)
  }

  const handleDetailClose = () => {
    setSelectedRecipeId(null)
  }

  const handleRunFromDetail = (recipe: RecipeResponse) => {
    const steps = parseStepsJson(recipe.stepsJson)
    const inputVars = extractInputVarsFromSteps(steps)

    if (inputVars.length === 0) {
      useChatStore.getState().executeRecipe(recipe.id, recipe.name, {})
      navigate('/')
    } else {
      // 변수 입력이 필요하면 목록으로 돌아가서 카드에서 실행 (기존 흐름 활용)
      setSelectedRecipeId(null)
    }
  }

  const handleDeleteFromDetail = (recipe: RecipeResponse) => {
    if (window.confirm(`"${recipe.name}" 레시피를 삭제하시겠습니까?`)) {
      deleteMutation.mutate(recipe.id)
    }
  }

  // 로딩 상태
  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center overflow-y-auto p-6">
        <div className="text-sm text-[var(--color-text-secondary)]">
          {MESSAGES.recipe.loading}
        </div>
      </div>
    )
  }

  // 에러 상태
  if (isError) {
    return (
      <div className="flex flex-1 items-center justify-center overflow-y-auto p-6">
        <div className="text-center">
          <div className="mb-2 text-3xl">❌</div>
          <div className="mb-1 text-lg font-semibold">
            {MESSAGES.common.fetchError}
          </div>
          <p className="text-sm text-[var(--color-text-secondary)]">
            {error instanceof Error
              ? error.message
              : MESSAGES.common.unknownError}
          </p>
        </div>
      </div>
    )
  }

  // 빈 상태 (Case 3)
  if (recipes.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center overflow-y-auto p-6">
        <div className="flex flex-col items-center p-12 text-center">
          <div className="mb-4 text-5xl">📋</div>
          <h2 className="mb-2 text-lg font-semibold">
            {MESSAGES.recipe.emptyTitle}
          </h2>
          <p className="mb-4 max-w-[400px] text-sm text-[var(--color-text-secondary)]">
            {MESSAGES.recipe.emptyDescription}
          </p>
          <Button
            variant="primary"
            size="md"
            onClick={() => navigate('/')}
          >
            {MESSAGES.recipe.goToChat}
          </Button>
        </div>
      </div>
    )
  }

  // 상세 보기 (Case 2)
  if (selectedRecipe) {
    return (
      <RecipeDetailPanel
        recipe={selectedRecipe}
        onBack={handleDetailClose}
        onRun={handleRunFromDetail}
        onDelete={handleDeleteFromDetail}
        isDeleting={deleteMutation.isPending}
      />
    )
  }

  // 목록 상태 (Case 1)
  return (
    <div className="flex-1 overflow-y-auto p-6">
      <div className="mx-auto max-w-[900px]">
        {/* 필터 바 */}
        <div className="mb-4 flex items-center gap-3">
          <input
            type="text"
            className="max-w-[300px] rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
            placeholder={MESSAGES.recipe.searchPlaceholder}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
          <select
            className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] focus:border-[var(--color-accent)] focus:outline-none"
            value={selectedTag}
            onChange={(e) => setSelectedTag(e.target.value)}
          >
            <option value="">전체 태그</option>
            {uniqueTags.map((tag) => (
              <option key={tag} value={tag}>
                {tag}
              </option>
            ))}
          </select>
        </div>

        {/* 자주 사용 섹션 */}
        {frequentRecipes.length > 0 && (
          <>
            <h3 className="mb-3 text-sm font-medium text-[var(--color-text-tertiary)]">
              {MESSAGES.recipe.frequentTitle}
            </h3>
            <div className="mb-6 flex flex-col gap-3">
              {frequentRecipes.map((recipe) => (
                <RecipeCard
                  key={recipe.id}
                  recipe={recipe}
                  onDetail={handleDetailOpen}
                />
              ))}
            </div>
          </>
        )}

        {/* 전체 섹션 */}
        <h3 className="mb-3 mt-6 text-sm font-medium text-[var(--color-text-tertiary)]">
          {MESSAGES.recipe.allTitle}
        </h3>
        <div className="flex flex-col gap-3">
          {filteredRecipes.map((recipe) => (
            <RecipeCard
              key={recipe.id}
              recipe={recipe}
              onDetail={handleDetailOpen}
            />
          ))}
        </div>

        {/* 검색 결과 없음 */}
        {filteredRecipes.length === 0 && searchQuery && (
          <div className="mt-8 text-center text-sm text-[var(--color-text-secondary)]">
            {MESSAGES.recipe.noSearchResult(searchQuery)}
          </div>
        )}
      </div>
    </div>
  )
}

export default RecipePage

// --- 상세 패널 컴포넌트 ---

interface RecipeDetailPanelProps {
  recipe: RecipeResponse
  onBack: () => void
  onRun: (recipe: RecipeResponse) => void
  onDelete: (recipe: RecipeResponse) => void
  isDeleting: boolean
}

function RecipeDetailPanel({
  recipe,
  onBack,
  onRun,
  onDelete,
  isDeleting,
}: RecipeDetailPanelProps) {
  const steps = parseStepsJson(recipe.stepsJson)
  const queryClient = useQueryClient()

  // 편집 모드 상태
  const [isEditing, setIsEditing] = useState(false)
  const [editName, setEditName] = useState(recipe.name)
  const [editDescription, setEditDescription] = useState(recipe.description)
  const [editTags, setEditTags] = useState(recipe.tags.join(', '))

  // 스펙 검증 결과 상태
  const [validationResult, setValidationResult] = useState<RecipeValidationResult | null>(null)

  const updateMutation = useMutation({
    mutationFn: (data: UpdateRecipeRequest) => updateRecipe(recipe.id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      setIsEditing(false)
    },
  })

  // 레시피 복제 mutation
  const cloneMutation = useMutation({
    mutationFn: () => cloneRecipe(recipe.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
    },
  })

  // 스펙 검증 mutation
  const validateMutation = useMutation({
    mutationFn: () => validateRecipe(recipe.id),
    onSuccess: (data) => {
      setValidationResult(data)
    },
  })

  const handleEditStart = () => {
    setEditName(recipe.name)
    setEditDescription(recipe.description)
    setEditTags(recipe.tags.join(', '))
    setIsEditing(true)
  }

  const handleEditCancel = () => {
    setIsEditing(false)
  }

  const handleEditSave = () => {
    const tags = editTags
      .split(',')
      .map((t) => t.trim())
      .filter((t) => t.length > 0)

    updateMutation.mutate({
      name: editName,
      description: editDescription,
      tags,
      stepsJson: recipe.stepsJson,
      visibility: recipe.visibility,
      variablesJson: recipe.variablesJson,
    })
  }

  return (
    <div className="flex-1 overflow-y-auto p-6">
      <div className="mx-auto max-w-[900px]">
        {/* 뒤로가기 */}
        <button
          type="button"
          className="mb-4 cursor-pointer border-none bg-transparent text-sm text-[var(--color-accent)] hover:text-[var(--color-accent-hover)]"
          onClick={onBack}
        >
          ← 목록으로
        </button>

        {/* 레시피명 + 설명 */}
        {isEditing ? (
          <div className="mb-4 flex flex-col gap-3">
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--color-text-tertiary)]">
                레시피명
              </label>
              <input
                type="text"
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
                value={editName}
                onChange={(e) => setEditName(e.target.value)}
                placeholder="레시피 이름"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--color-text-tertiary)]">
                설명
              </label>
              <input
                type="text"
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                placeholder="레시피 설명"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium text-[var(--color-text-tertiary)]">
                태그 (콤마로 구분)
              </label>
              <input
                type="text"
                className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
                value={editTags}
                onChange={(e) => setEditTags(e.target.value)}
                placeholder="채용, 회원, 결제"
              />
            </div>
          </div>
        ) : (
          <>
            <h2 className="mb-2 text-xl font-bold text-[var(--color-text-primary)]">
              📋 {recipe.name}
            </h2>
            <p className="mb-4 text-sm text-[var(--color-text-secondary)]">
              {recipe.description}
            </p>
          </>
        )}

        {/* 태그 + 메타 정보 (보기 모드에서만) */}
        {!isEditing && (
          <div className="mb-4 flex flex-wrap items-center gap-3">
            {recipe.tags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-[var(--color-bg-tertiary)] px-2 py-0.5 text-xs text-[var(--color-text-secondary)]"
              >
                #{tag}
              </span>
            ))}
            <span className="text-xs text-[var(--color-text-tertiary)]">
              총 {recipe.usageCount}회 사용
              {recipe.lastUsedAt && ` · 마지막 사용: ${formatDate(recipe.lastUsedAt)}`}
            </span>
          </div>
        )}

        {/* 생성일 */}
        <div className="mb-4 text-xs text-[var(--color-text-tertiary)]">
          생성일: {formatDate(recipe.createdAt)}
        </div>

        {/* 스펙 검증 */}
        {!isEditing && (
          <div className="mb-4">
            <Button
              variant="secondary"
              size="md"
              disabled={validateMutation.isPending}
              onClick={() => validateMutation.mutate()}
            >
              {validateMutation.isPending ? '검증 중...' : '🔍 스펙 검증'}
            </Button>

            {/* 검증 에러 */}
            {validateMutation.isError && (
              <div className="mt-2 rounded-lg bg-[var(--color-error-subtle)] px-3 py-2 text-sm text-[var(--color-error)]">
                {validateMutation.error instanceof Error
                  ? validateMutation.error.message
                  : '스펙 검증에 실패했습니다.'}
              </div>
            )}

            {/* 검증 결과 */}
            {validationResult && (
              <div className="mt-2">
                {validationResult.status === 'VALID' && (
                  <div className="rounded-lg bg-[var(--color-success-subtle)] px-3 py-2 text-sm text-[var(--color-success)]">
                    ✅ 모든 step이 현재 스펙과 호환됩니다.
                  </div>
                )}
                {validationResult.status === 'WARN' && (
                  <div className="rounded-lg bg-[var(--color-warning-subtle)] px-3 py-2 text-sm text-[var(--color-warning)]">
                    <div className="mb-1 font-medium">⚠️ 경고</div>
                    <ul className="list-inside list-disc space-y-1">
                      {validationResult.issues.map((issue, idx) => (
                        <li key={idx}>
                          Step {issue.stepIndex + 1} [{issue.type}]: {issue.message}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {validationResult.status === 'BROKEN' && (
                  <div className="rounded-lg bg-[var(--color-error-subtle)] px-3 py-2 text-sm text-[var(--color-error)]">
                    <div className="mb-1 font-medium">❌ 호환 불가</div>
                    <ul className="list-inside list-disc space-y-1">
                      {validationResult.issues.map((issue, idx) => (
                        <li key={idx}>
                          Step {issue.stepIndex + 1} [{issue.type}]: {issue.message}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* 단계별 시각화 */}
        <div className="my-4 flex flex-col gap-2">
          {steps.map((step, index) => {
            return (
              <div
                key={index}
                className="flex items-start gap-3 rounded-md bg-[var(--color-bg-tertiary)] p-3 text-sm"
              >
                {/* Step 번호 배지 */}
                <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[var(--color-accent-subtle)] text-xs font-bold text-[var(--color-accent)]">
                  {index + 1}
                </div>
                {/* 내용 */}
                <div className="flex-1">
                  <div className="font-medium text-[var(--color-text-primary)]">
                    {step.name ?? step.subdomain}
                  </div>
                  <div className="mt-0.5 text-xs text-[var(--color-text-tertiary)]">
                    {step.subdomain}{step.environment ? ` (${step.environment})` : ''}
                  </div>
                  <div className="mt-1 font-mono text-xs text-[var(--color-text-secondary)]">
                    {resolveMethod(step)} {resolvePath(step)}
                  </div>
                  {/* body 변수 표시 */}
                  {renderBodyVariables(step)}
                  {/* extract 표시 */}
                  {step.extract && Object.keys(step.extract).length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
                      {Object.entries(step.extract).map(([key, jsonPath]) => (
                        <span key={key} className="text-xs">
                          <span className="text-[var(--color-text-tertiary)]">→ {key}: </span>
                          <span className="font-mono text-[var(--color-text-tertiary)]">{jsonPath}</span>
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {/* 복제 성공 메시지 */}
        {cloneMutation.isSuccess && (
          <div className="mb-3 rounded-lg bg-[var(--color-success-subtle)] px-3 py-2 text-sm text-[var(--color-success)]">
            레시피가 복제되었습니다.
          </div>
        )}

        {/* 복제 에러 표시 */}
        {cloneMutation.isError && (
          <div className="mb-3 rounded-lg bg-[var(--color-error-subtle)] px-3 py-2 text-sm text-[var(--color-error)]">
            {cloneMutation.error instanceof Error
              ? cloneMutation.error.message
              : '레시피 복제에 실패했습니다.'}
          </div>
        )}

        {/* 저장 에러 표시 */}
        {updateMutation.isError && (
          <div className="mb-3 rounded-lg bg-[var(--color-error-subtle)] px-3 py-2 text-sm text-[var(--color-error)]">
            {updateMutation.error instanceof Error
              ? updateMutation.error.message
              : '레시피 수정에 실패했습니다.'}
          </div>
        )}

        {/* 액션 버튼 */}
        <div className="flex gap-2">
          {isEditing ? (
            <>
              <Button
                variant="primary"
                size="md"
                disabled={updateMutation.isPending}
                onClick={handleEditSave}
              >
                {updateMutation.isPending ? '저장 중...' : '저장'}
              </Button>
              <Button
                variant="secondary"
                size="md"
                disabled={updateMutation.isPending}
                onClick={handleEditCancel}
              >
                취소
              </Button>
            </>
          ) : (
            <>
              <Button variant="primary" size="md" onClick={() => onRun(recipe)}>
                ▶ 실행
              </Button>
              <Button variant="secondary" size="md" onClick={handleEditStart}>
                수정
              </Button>
              <Button
                variant="secondary"
                size="md"
                disabled={cloneMutation.isPending}
                onClick={() => cloneMutation.mutate()}
              >
                {cloneMutation.isPending ? '복제 중...' : '복제'}
              </Button>
              <Button
                variant="danger"
                size="md"
                disabled={isDeleting}
                onClick={() => onDelete(recipe)}
              >
                {isDeleting ? '삭제 중...' : '삭제'}
              </Button>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

// --- 변수 라벨 컴포넌트 ---

interface VariableLabelProps {
  value: string
}

/** 변수 패턴에 따라 색상 구분: {{input:*}} → 보라색, {{gen:*}} / {{변수명}} → 회색 */
function VariableLabel({ value }: VariableLabelProps) {
  // {{input:라벨}} 패턴 — 보라색 (사용자 입력 변수)
  const inputPattern = /\{\{input:([^}]+)\}\}/
  // {{gen:*}} 패턴 — 회색 (자동 생성)
  const genPattern = /\{\{gen:[^}]+\}\}/
  // {{변수명}} 패턴 — 회색 (이전 step 참조)
  const refPattern = /\{\{[^}]+\}\}/

  if (inputPattern.test(value)) {
    return (
      <span className="font-mono text-xs text-purple-400">
        {value}
      </span>
    )
  }

  if (genPattern.test(value) || refPattern.test(value)) {
    return (
      <span className="font-mono text-xs text-[var(--color-text-tertiary)]">
        {value}
      </span>
    )
  }

  // 일반 값
  return (
    <span className="font-mono text-xs text-[var(--color-text-secondary)]">
      {value}
    </span>
  )
}

// --- 유틸리티 함수 ---

/** stepsJson을 파싱하여 RecipeStep 배열로 변환 */
function parseStepsJson(stepsJson: string): RecipeStep[] {
  try {
    const parsed: unknown = JSON.parse(stepsJson)
    if (Array.isArray(parsed)) {
      return parsed as RecipeStep[]
    }
    return []
  } catch {
    return []
  }
}

/** api 필드 ("POST /api/members") → { method, path } 파싱 (구버전 호환) */
function parseApi(api: string): { method: string; path: string } {
  const spaceIndex = api.indexOf(' ')
  if (spaceIndex === -1) {
    return { method: '', path: api }
  }
  return {
    method: api.substring(0, spaceIndex),
    path: api.substring(spaceIndex + 1),
  }
}

/** step에서 method를 추출 (신규 필드 우선, 구버전 fallback) */
function resolveMethod(step: RecipeStep): string {
  if (step.method) return step.method
  if (step.api) return parseApi(step.api).method
  return ''
}

/** step에서 path를 추출 (신규 필드 우선, 구버전 fallback) */
function resolvePath(step: RecipeStep): string {
  if (step.path) return step.path
  if (step.api) return parseApi(step.api).path
  return ''
}

/** step의 body 또는 params에서 변수 라벨을 렌더링 */
function renderBodyVariables(step: RecipeStep) {
  // 신규 body 필드 우선
  if (step.body && Object.keys(step.body).length > 0) {
    return (
      <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
        {Object.entries(step.body).map(([key, value]) => (
          <span key={key} className="text-xs">
            <span className="text-[var(--color-text-tertiary)]">{key}: </span>
            <VariableLabel value={String(value ?? '')} />
          </span>
        ))}
      </div>
    )
  }
  // 구버전 params fallback
  if (step.params && Object.keys(step.params).length > 0) {
    return (
      <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
        {Object.entries(step.params).map(([key, value]) => (
          <span key={key} className="text-xs">
            <span className="text-[var(--color-text-tertiary)]">{key}: </span>
            <VariableLabel value={value} />
          </span>
        ))}
      </div>
    )
  }
  return null
}

/** steps에서 {{input:라벨}} 패턴의 라벨 목록을 중복 없이 추출 */
function extractInputVarsFromSteps(steps: RecipeStep[]): string[] {
  const labels = new Set<string>()
  const pattern = /\{\{input:([^}]+)\}\}/g

  for (const step of steps) {
    // 신규 body 필드에서 추출
    if (step.body) {
      for (const value of Object.values(step.body)) {
        if (typeof value !== 'string') continue
        let match: RegExpExecArray | null
        while ((match = pattern.exec(value)) !== null) {
          labels.add(match[1])
        }
        pattern.lastIndex = 0
      }
    }
    // 구버전 params fallback
    if (step.params) {
      for (const value of Object.values(step.params)) {
        let match: RegExpExecArray | null
        while ((match = pattern.exec(value)) !== null) {
          labels.add(match[1])
        }
        pattern.lastIndex = 0
      }
    }
  }

  return Array.from(labels)
}

/** 날짜 문자열을 읽기 좋은 형식으로 변환 */
function formatDate(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()

  // 오늘인 경우
  if (date.toDateString() === now.toDateString()) {
    return '오늘'
  }

  // 어제인 경우
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return '어제'
  }

  // 그 외
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
