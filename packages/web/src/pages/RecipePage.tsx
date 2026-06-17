import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getRecipes } from '@/services/recipeApi'
import RecipeCard from '@/components/recipe/RecipeCard'
import { Button } from '@/components/ui'
import { MESSAGES } from '@/constants'

function RecipePage() {
  const [searchQuery, setSearchQuery] = useState('')
  const navigate = useNavigate()

  const {
    data: recipes = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['recipes'],
    queryFn: getRecipes,
  })

  // 검색 필터: 레시피명, 설명, 태그로 필터
  const filteredRecipes = recipes.filter((recipe) => {
    const query = searchQuery.toLowerCase()
    return (
      recipe.name.toLowerCase().includes(query) ||
      recipe.description.toLowerCase().includes(query) ||
      recipe.tags.some((tag) => tag.toLowerCase().includes(query))
    )
  })

  // "자주 사용" 섹션: usageCount > 0인 레시피, 사용 횟수 순 정렬
  const frequentRecipes = [...filteredRecipes]
    .filter((r) => r.usageCount > 0)
    .sort((a, b) => b.usageCount - a.usageCount)

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
        </div>

        {/* 자주 사용 섹션 */}
        {frequentRecipes.length > 0 && (
          <>
            <h3 className="mb-3 text-sm font-medium text-[var(--color-text-tertiary)]">
              {MESSAGES.recipe.frequentTitle}
            </h3>
            <div className="mb-6 flex flex-col gap-3">
              {frequentRecipes.map((recipe) => (
                <RecipeCard key={recipe.id} recipe={recipe} />
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
            <RecipeCard key={recipe.id} recipe={recipe} />
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
