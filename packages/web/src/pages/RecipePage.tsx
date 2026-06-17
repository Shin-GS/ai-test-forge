import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getRecipes, deleteRecipe } from '@/services/recipeApi'
import { useChatStore } from '@/stores/useChatStore'
import RecipeCard from '@/components/recipe/RecipeCard'
import { Button } from '@/components/ui'
import { MESSAGES } from '@/constants'
import type { RecipeResponse, RecipeStep } from '@/types/recipe'

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
        <h2 className="mb-2 text-xl font-bold text-[var(--color-text-primary)]">
          📋 {recipe.name}
        </h2>
        <p className="mb-4 text-sm text-[var(--color-text-secondary)]">
          {recipe.description}
        </p>

        {/* 태그 + 메타 정보 */}
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

        {/* 생성일 */}
        <div className="mb-4 text-xs text-[var(--color-text-tertiary)]">
          생성일: {formatDate(recipe.createdAt)}
        </div>

        {/* 단계별 시각화 */}
        <div className="my-4 flex flex-col gap-2">
          {steps.map((step, index) => {
            const { method, path } = parseApi(step.api)
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
                    {step.subdomain}
                  </div>
                  <div className="mt-1 font-mono text-xs text-[var(--color-text-secondary)]">
                    {method} {path}
                  </div>
                  {/* 변수 표시 */}
                  {step.params && Object.keys(step.params).length > 0 && (
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-1">
                      {Object.entries(step.params).map(([key, value]) => (
                        <span key={key} className="text-xs">
                          <span className="text-[var(--color-text-tertiary)]">{key}: </span>
                          <VariableLabel value={value} />
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {/* 액션 버튼 */}
        <div className="flex gap-2">
          <Button variant="primary" size="md" onClick={() => onRun(recipe)}>
            ▶ 실행
          </Button>
          <Button
            variant="danger"
            size="md"
            disabled={isDeleting}
            onClick={() => onDelete(recipe)}
          >
            {isDeleting ? '삭제 중...' : '삭제'}
          </Button>
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

/** api 필드 ("POST /api/members") → { method, path } 파싱 */
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

/** steps에서 {{input:라벨}} 패턴의 라벨 목록을 중복 없이 추출 */
function extractInputVarsFromSteps(steps: RecipeStep[]): string[] {
  const labels = new Set<string>()
  const pattern = /\{\{input:([^}]+)\}\}/g

  for (const step of steps) {
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
