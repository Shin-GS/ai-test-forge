import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getSpecs } from '@/services/specApi'
import SubdomainCard from '@/components/subdomain/SubdomainCard'
import { Button } from '@/components/ui'

function SubdomainPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [showStaleBanner, setShowStaleBanner] = useState(true)

  const {
    data: specs = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['specs'],
    queryFn: getSpecs,
  })

  // 검색 필터
  const filteredSpecs = specs.filter((spec) =>
    spec.name.toLowerCase().includes(searchQuery.toLowerCase()),
  )

  // ACTIVE 우선, STALE 하단 정렬
  const sortedSpecs = [...filteredSpecs].sort((a, b) => {
    if (a.status === 'ACTIVE' && b.status !== 'ACTIVE') return -1
    if (a.status !== 'ACTIVE' && b.status === 'ACTIVE') return 1
    return 0
  })

  const staleCount = specs.filter((s) => s.status === 'STALE').length

  // 로딩 상태
  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center overflow-y-auto p-6">
        <div className="text-sm text-[var(--color-text-secondary)]">
          서브도메인 목록을 불러오는 중...
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
  if (specs.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center overflow-y-auto p-6">
        <div className="flex flex-col items-center text-center">
          <div className="mb-4 text-5xl">📡</div>
          <h2 className="mb-2 text-lg font-semibold">
            등록된 서브도메인이 없습니다
          </h2>
          <p className="mb-4 max-w-[400px] text-sm text-[var(--color-text-secondary)]">
            서브도메인 서버에 클라이언트 라이브러리를 추가하면 자동으로
            등록됩니다.
          </p>
          <Button
            variant="primary"
            size="md"
          >
            연동 가이드 보기
          </Button>
        </div>
      </div>
    )
  }

  // 목록 상태 (Case 1 + Case 4)
  return (
    <div className="flex-1 overflow-y-auto p-6">
      <div className="mx-auto max-w-[900px]">
        {/* STALE 경고 배너 (Case 4) */}
        {staleCount > 0 && showStaleBanner && (
          <div className="mb-4 flex items-center justify-between rounded-md border border-[rgba(245,158,11,0.3)] bg-[var(--color-warning-subtle)] px-4 py-3 text-sm">
            <span>⚠️ {staleCount}개 서버의 연결이 불안정합니다</span>
            <button
              type="button"
              className="text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
              onClick={() => setShowStaleBanner(false)}
              aria-label="경고 배너 닫기"
            >
              ✕
            </button>
          </div>
        )}

        {/* 필터 바 */}
        <div className="mb-4 flex items-center gap-3">
          <input
            type="text"
            className="max-w-[300px] rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
            placeholder="🔍 서버 검색..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        {/* 카드 목록 */}
        <div className="flex flex-col gap-3">
          {sortedSpecs.map((spec) => (
            <SubdomainCard key={spec.id} spec={spec} />
          ))}
        </div>

        {/* 검색 결과 없음 */}
        {filteredSpecs.length === 0 && searchQuery && (
          <div className="mt-8 text-center text-sm text-[var(--color-text-secondary)]">
            "{searchQuery}"에 대한 검색 결과가 없습니다.
          </div>
        )}
      </div>
    </div>
  )
}

export default SubdomainPage
