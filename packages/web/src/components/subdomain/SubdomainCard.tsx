import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { SpecResponse } from '@/types/spec'
import { formatRelativeTime } from '@/utils/formatRelativeTime'

interface SubdomainCardProps {
  spec: SpecResponse
}

function SubdomainCard({ spec }: SubdomainCardProps) {
  const [isExpanded, setIsExpanded] = useState(false)
  const navigate = useNavigate()
  const isStale = spec.status === 'STALE'

  const handleEnvironmentClick = () => {
    navigate(
      `/subdomains/${encodeURIComponent(spec.name)}?environment=${encodeURIComponent(spec.environment)}`,
    )
  }

  return (
    <div
      className={`overflow-hidden rounded-lg border bg-[var(--color-bg-secondary)] ${
        isStale
          ? 'border-[rgba(245,158,11,0.3)]'
          : 'border-[var(--color-border)]'
      }`}
    >
      <button
        type="button"
        className="flex w-full cursor-pointer items-center justify-between p-4 hover:bg-[var(--color-bg-hover)]"
        onClick={() => setIsExpanded(!isExpanded)}
        aria-expanded={isExpanded}
        aria-label={`${spec.name} 상세 ${isExpanded ? '접기' : '펼치기'}`}
      >
        <div className="flex items-center gap-2 font-semibold">
          <span>📡</span>
          <span>{spec.name}</span>
        </div>
        <div className="flex items-center gap-3 text-sm text-[var(--color-text-secondary)]">
          <StatusBadge status={spec.status} />
          <span>{spec.environment}</span>
          {isStale && (
            <span className="text-xs text-[var(--color-text-tertiary)]">
              {formatRelativeTime(spec.lastHeartbeatAt)}
            </span>
          )}
          <span>{isExpanded ? '▲' : '▼'}</span>
        </div>
      </button>

      {isExpanded && (
        <div className="border-t border-[var(--color-border)] px-4 pb-4 pt-3">
          {spec.description && (
            <p className="mb-3 text-sm text-[var(--color-text-secondary)]">
              {spec.description}
            </p>
          )}
          <div
            className="mb-2 flex cursor-pointer items-center justify-between rounded-md bg-[var(--color-bg-tertiary)] px-3 py-2 text-sm hover:bg-[var(--color-bg-hover)]"
            onClick={handleEnvironmentClick}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                handleEnvironmentClick()
              }
            }}
            aria-label={`${spec.name} ${spec.environment} 환경 상세 보기`}
          >
            <div className="flex items-center gap-2">
              <span className="text-[var(--color-accent)]">●</span>
              <span>{spec.environment}</span>
            </div>
            <span className="text-xs text-[var(--color-text-tertiary)]">
              {formatRelativeTime(spec.lastHeartbeatAt)}
            </span>
          </div>
          <div className="mt-3 text-xs text-[var(--color-text-tertiary)]">
            <span className="font-mono">{spec.baseUrl}</span>
          </div>
        </div>
      )}
    </div>
  )
}

interface StatusBadgeProps {
  status: SpecResponse['status']
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

export default SubdomainCard
