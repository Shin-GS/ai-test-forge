import { useNavigate } from 'react-router-dom'
import type { RecipeResponse, RecipeStep } from '@/types/recipe'

interface RecipeCardProps {
  recipe: RecipeResponse
}

function RecipeCard({ recipe }: RecipeCardProps) {
  const navigate = useNavigate()

  // stepsJson 파싱
  const steps = parseSteps(recipe.stepsJson)
  const stepCount = steps.length
  const variableCount = countVariables(steps)

  const handleRun = () => {
    // 채팅 탭으로 이동 (추후 레시피 실행 로직 연동)
    navigate('/')
  }

  const handleDetail = () => {
    // 상세 보기 placeholder (미구현)
  }

  return (
    <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-4 transition-all hover:border-[var(--color-border-light)]">
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
      </div>
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

/** steps에서 사용자 입력 변수({{input:...}}) 개수를 카운트 */
function countVariables(steps: RecipeStep[]): number {
  let count = 0
  for (const step of steps) {
    if (step.params) {
      for (const value of Object.values(step.params)) {
        if (value.startsWith('{{input:')) {
          count++
        }
      }
    }
  }
  return count
}

export default RecipeCard
