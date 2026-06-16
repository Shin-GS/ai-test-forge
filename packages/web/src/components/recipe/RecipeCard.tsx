import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { RecipeResponse, RecipeStep } from '@/types/recipe'
import { deleteRecipe } from '@/services/recipeApi'
import { useChatStore } from '@/stores/useChatStore'

interface RecipeCardProps {
  recipe: RecipeResponse
}

function RecipeCard({ recipe }: RecipeCardProps) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [showVariableForm, setShowVariableForm] = useState(false)
  const [variableValues, setVariableValues] = useState<Record<string, string>>({})

  const deleteMutation = useMutation({
    mutationFn: () => deleteRecipe(recipe.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
    },
  })

  // stepsJson 파싱
  const steps = parseSteps(recipe.stepsJson)
  const stepCount = steps.length
  const inputVariables = extractInputVariables(steps)
  const variableCount = inputVariables.length

  const handleRun = () => {
    if (variableCount === 0) {
      // 변수 없으면 바로 실행
      useChatStore.getState().executeRecipe(recipe.id, recipe.name, {})
      navigate('/')
    } else {
      // 변수 있으면 입력 폼 표시
      const initialValues: Record<string, string> = {}
      for (const label of inputVariables) {
        initialValues[label] = ''
      }
      setVariableValues(initialValues)
      setShowVariableForm(true)
    }
  }

  const handleVariableChange = (label: string, value: string) => {
    setVariableValues((prev) => ({ ...prev, [label]: value }))
  }

  const handleExecuteWithVariables = () => {
    useChatStore.getState().executeRecipe(recipe.id, recipe.name, variableValues)
    setShowVariableForm(false)
    navigate('/')
  }

  const handleCancelVariableForm = () => {
    setShowVariableForm(false)
    setVariableValues({})
  }

  const allFieldsFilled = Object.values(variableValues).every(
    (v) => v.trim().length > 0
  )

  const handleDetail = () => {
    // 상세 보기 placeholder
  }

  const handleDelete = () => {
    if (window.confirm(`"${recipe.name}" 레시피를 삭제하시겠습니까?`)) {
      deleteMutation.mutate()
    }
  }

  return (
    <div className="overflow-hidden rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] transition-all hover:border-[var(--color-border-light)]">
      {/* 카드 본문 */}
      <div className="p-4">
        {/* 헤더: 레시피명 + 사용 횟수 */}
        <div className="mb-2 flex items-center justify-between">
          <div className="flex items-center gap-2 font-semibold">
            <span>📋</span>
            <span>{recipe.name}</span>
          </div>
          {recipe.usageCount > 0 && (
            <div className="text-xs text-[var(--color-text-tertiary)]">
              ⭐ ×{recipe.usageCount}
            </div>
          )}
        </div>

        {/* 설명 */}
        <div className="mb-3 text-sm text-[var(--color-text-secondary)]">
          {recipe.description}
        </div>

        {/* 메타: 태그 + 단계 수 + 변수 수 */}
        <div className="mb-3 flex items-center gap-3 text-xs text-[var(--color-text-tertiary)]">
          {recipe.tags.length > 0 && (
            <div className="flex gap-1">
              {recipe.tags.map((tag) => (
                <span
                  key={tag}
                  className="rounded-full bg-[var(--color-bg-tertiary)] px-2 py-0.5 text-xs text-[var(--color-text-secondary)]"
                >
                  #{tag}
                </span>
              ))}
            </div>
          )}
          <span>{stepCount}단계</span>
          <span>
            변수 {variableCount}개{variableCount === 0 && ' (전체 자동)'}
          </span>
        </div>

        {/* 액션 버튼 */}
        <div className="flex gap-2">
          <button
            type="button"
            className="rounded-lg bg-[var(--color-accent)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)]"
            onClick={handleRun}
          >
            ▶ 실행
          </button>
          <button
            type="button"
            className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-hover)]"
            onClick={handleDetail}
          >
            상세 보기
          </button>
          <button
            type="button"
            disabled={deleteMutation.isPending}
            className="ml-auto rounded-lg px-3 py-1.5 text-sm text-[var(--color-error)] hover:bg-[var(--color-error-subtle)] disabled:opacity-50"
            onClick={handleDelete}
          >
            {deleteMutation.isPending ? '삭제 중...' : '삭제'}
          </button>
        </div>
      </div>

      {/* 변수 입력 폼 (인라인 펼침) */}
      {showVariableForm && (
        <div className="border-t border-[var(--color-border)] bg-[var(--color-bg-tertiary)] p-3">
          <div className="mb-2 text-xs font-medium text-[var(--color-text-secondary)]">
            실행에 필요한 변수를 입력하세요
          </div>
          <div className="flex flex-col gap-2">
            {inputVariables.map((label) => (
              <div key={label}>
                <label className="mb-1 block text-xs font-medium text-[var(--color-text-secondary)]">
                  {label}
                </label>
                <input
                  type="text"
                  value={variableValues[label] ?? ''}
                  onChange={(e) => handleVariableChange(label, e.target.value)}
                  placeholder={`${label} 입력`}
                  className="w-full rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-tertiary)] px-3 py-1.5 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
                />
              </div>
            ))}
          </div>
          <div className="mt-3 flex gap-2">
            <button
              type="button"
              disabled={!allFieldsFilled}
              className="rounded-lg bg-[var(--color-accent)] px-3 py-1.5 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)] disabled:cursor-not-allowed disabled:opacity-50"
              onClick={handleExecuteWithVariables}
            >
              실행
            </button>
            <button
              type="button"
              className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-1.5 text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-bg-hover)]"
              onClick={handleCancelVariableForm}
            >
              취소
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/** stepsJson을 파싱하여 RecipeStep 배열로 변환 */
function parseSteps(stepsJson: string): RecipeStep[] {
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

/** steps에서 {{input:라벨}} 패턴의 라벨 목록을 중복 없이 추출 */
function extractInputVariables(steps: RecipeStep[]): string[] {
  const labels = new Set<string>()
  const pattern = /\{\{input:([^}]+)\}\}/g

  for (const step of steps) {
    if (step.params) {
      for (const value of Object.values(step.params)) {
        let match: RegExpExecArray | null
        while ((match = pattern.exec(value)) !== null) {
          labels.add(match[1])
        }
        // RegExp.exec은 lastIndex를 사용하므로 리셋
        pattern.lastIndex = 0
      }
    }
  }

  return Array.from(labels)
}

export default RecipeCard
