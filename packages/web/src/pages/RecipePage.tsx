import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { getRecipes } from '@/services/recipeApi'
import RecipeCard from '@/components/recipe/RecipeCard'
import { Button } from '@/components/ui'

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
          레시피 목록을 불러오는 중...
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
            데이터를 불러올 수 없습니다
          </div>
          <p className="text-sm text-[var(--color-text-secondary)]">
            {error instanceof Error
              ? error.message
              : '알 수 없는 오류가 발생했습니다.'}
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
            저장된 레시피가 없습니다
          </h2>
          <p className="mb-4 max-w-[400px] text-sm text-[var(--color-text-secondary)]">
            채팅에서 작업 후 "레시피로 저장해줘"라고 말해보세요.
          </p>
          <Button
            variant="primary"
            size="md"
            onClick={() => navigate('/')}
          >
            💬 채팅으로 이동
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
            placeholder="🔍 레시피 검색..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        {/* 자주 사용 섹션 */}
        {frequentRecipes.length > 0 && (
          <>
            <h3 className="mb-3 text-sm font-medium text-[var(--color-text-tertiary)]">
              ⭐ 자주 사용
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
          전체
        </h3>
        <div className="flex flex-col gap-3">
          {filteredRecipes.map((recipe) => (
            <RecipeCard key={recipe.id} recipe={recipe} />
          ))}
        </div>

        {/* 검색 결과 없음 */}
        {filteredRecipes.length === 0 && searchQuery && (
          <div className="mt-8 text-center text-sm text-[var(--color-text-secondary)]">
            "{searchQuery}"에 대한 검색 결과가 없습니다.
          </div>
        )}
      </div>
    </div>
  )
}

export default RecipePage
