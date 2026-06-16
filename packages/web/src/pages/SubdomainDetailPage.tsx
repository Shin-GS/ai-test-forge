import { useState, useMemo } from 'react'
import { useParams, useSearchParams, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getSpecDetail } from '@/services/specApi'
import { formatRelativeTime } from '@/utils/formatRelativeTime'
import type { ApiEndpointResponse } from '@/types/spec'

/** method별 색상 매핑 */
const METHOD_COLOR_MAP: Record<string, string> = {
  GET: 'text-[var(--color-success)]',
  POST: 'text-[var(--color-info)]',
  PUT: 'text-[var(--color-warning)]',
  DELETE: 'text-[var(--color-error)]',
}

function SubdomainDetailPage() {
  const { name } = useParams<{ name: string }>()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const environment = searchParams.get('environment') ?? 'default'

  const [apiSearch, setApiSearch] = useState('')
  const [collapsedTags, setCollapsedTags] = useState<Set<string>>(new Set())

  const {
    data: detail,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['specDetail', name, environment],
    queryFn: () => getSpecDetail(name!, environment),
    enabled: !!name,
  })

  // API 검색 필터 (path 또는 summary)
  const filteredEndpoints = useMemo(() => {
    if (!detail) return []
    if (!apiSearch.trim()) return detail.endpoints

    const query = apiSearch.toLowerCase()
    return detail.endpoints.filter(
      (ep) =>
        ep.path.toLowerCase().includes(query) ||
        ep.summary.toLowerCase().includes(query),
    )
  }, [detail, apiSearch])

  // 태그별 그룹핑
  const groupedByTag = useMemo(() => {
    const map = new Map<string, ApiEndpointResponse[]>()
    for (const ep of filteredEndpoints) {
      const tag = ep.tag || '기타'
      if (!map.has(tag)) {
        map.set(tag, [])
      }
      map.get(tag)!.push(ep)
    }
    return map
  }, [filteredEndpoints])

  const toggleTag = (tag: string) => {
    setCollapsedTags((prev) => {
      const next = new Set(prev)
      if (next.has(tag)) {
        next.delete(tag)
      } else {
        next.add(tag)
      }
      return next
    })
  }

  const handleUseChatClick = () => {
    navigate('/')
  }

  // 로딩 상태
  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center overflow-y-auto p-6">
        <div className="text-sm text-[var(--color-text-secondary)]">
          서브도메인 정보를 불러오는 중...
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
          <button
            type="button"
            className="mt-4 text-sm text-[var(--color-accent)] hover:text-[var(--color-accent-hover)]"
            onClick={() => navigate('/subdomains')}
          >
            ← 목록으로
          </button>
        </div>
      </div>
    )
  }

  if (!detail) return null

  return (
    <div className="flex-1 overflow-y-auto p-6">
      <div className="mx-auto max-w-[900px]">
        {/* 헤더: 뒤로 + 서비스명 */}
        <div className="mb-4 flex items-center gap-3">
          <button
            type="button"
            className="cursor-pointer border-none bg-transparent text-sm text-[var(--color-accent)] hover:text-[var(--color-accent-hover)]"
            onClick={() => navigate('/subdomains')}
          >
            ← 목록으로
          </button>
          <h2 className="text-lg font-semibold">
            📡 {detail.name}{' '}
            <span className="font-normal text-[var(--color-text-secondary)]">
              ({detail.environment})
            </span>
          </h2>
        </div>

        {/* 상태 정보 바 */}
        <div className="mb-4 flex flex-wrap items-center gap-4 text-sm text-[var(--color-text-secondary)]">
          <StatusBadge status={detail.status} />
          <span>API {detail.apiCount}개</span>
          <span>마지막 갱신: {formatRelativeTime(detail.lastHeartbeatAt)}</span>
          <span className="font-mono text-xs">{detail.baseUrl}</span>
        </div>

        {/* 설명 카드 */}
        {detail.description && (
          <div className="mb-4 rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-3">
            <p className="text-sm text-[var(--color-text-secondary)]">
              {detail.description}
            </p>
          </div>
        )}

        {/* API 검색 바 */}
        <input
          type="text"
          className="mb-4 max-w-[400px] rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] px-3 py-2 text-sm text-[var(--color-text-primary)] placeholder:text-[var(--color-text-tertiary)] focus:border-[var(--color-accent)] focus:outline-none"
          placeholder="🔍 API 검색..."
          value={apiSearch}
          onChange={(e) => setApiSearch(e.target.value)}
        />

        {/* API 목록 (태그별 그룹핑) */}
        {groupedByTag.size > 0 ? (
          Array.from(groupedByTag.entries()).map(([tag, endpoints]) => (
            <div key={tag} className="mb-4">
              <button
                type="button"
                className="mb-2 flex cursor-pointer items-center gap-2 border-none bg-transparent text-sm font-semibold text-[var(--color-text-secondary)] hover:text-[var(--color-text-primary)]"
                onClick={() => toggleTag(tag)}
                aria-expanded={!collapsedTags.has(tag)}
                aria-label={`${tag} 그룹 ${collapsedTags.has(tag) ? '펼치기' : '접기'}`}
              >
                <span>📁 {tag} ({endpoints.length}개)</span>
                <span className="text-xs">
                  {collapsedTags.has(tag) ? '▶' : '▼'}
                </span>
              </button>
              {!collapsedTags.has(tag) &&
                endpoints.map((ep, idx) => (
                  <div
                    key={`${ep.method}-${ep.path}-${idx}`}
                    className="flex items-center gap-3 rounded-sm px-3 py-2 text-sm hover:bg-[var(--color-bg-hover)]"
                  >
                    <span
                      className={`w-[50px] font-mono text-xs font-bold ${METHOD_COLOR_MAP[ep.method.toUpperCase()] ?? 'text-[var(--color-text-secondary)]'}`}
                    >
                      {ep.method.toUpperCase()}
                    </span>
                    <span className="font-mono text-xs">{ep.path}</span>
                    <span className="ml-auto text-xs text-[var(--color-text-tertiary)]">
                      {ep.summary}
                    </span>
                  </div>
                ))}
            </div>
          ))
        ) : (
          <div className="mt-8 text-center text-sm text-[var(--color-text-secondary)]">
            {apiSearch
              ? `"${apiSearch}"에 대한 검색 결과가 없습니다.`
              : 'API 엔드포인트가 없습니다.'}
          </div>
        )}

        {/* 채팅에서 사용하기 버튼 */}
        <button
          type="button"
          className="mt-4 rounded-lg bg-[var(--color-accent)] px-4 py-2 text-sm font-medium text-white hover:bg-[var(--color-accent-hover)]"
          onClick={handleUseChatClick}
        >
          💬 채팅에서 이 서버 사용하기 →
        </button>
      </div>
    </div>
  )
}

interface StatusBadgeProps {
  status: string
}

function StatusBadge({ status }: StatusBadgeProps) {
  if (status === 'ACTIVE') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-[var(--color-success-subtle)] px-2 py-0.5 text-xs font-medium text-[var(--color-success)]">
        ✅ ACTIVE
      </span>
    )
  }

  if (status === 'STALE') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-[var(--color-warning-subtle)] px-2 py-0.5 text-xs font-medium text-[var(--color-warning)]">
        ⚠️ STALE
      </span>
    )
  }

  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-[var(--color-info-subtle)] px-2 py-0.5 text-xs font-medium text-[var(--color-info)]">
      ⏳ REGISTERING
    </span>
  )
}

export default SubdomainDetailPage
