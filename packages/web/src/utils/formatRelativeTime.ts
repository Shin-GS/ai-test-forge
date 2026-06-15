/**
 * ISO 문자열을 상대 시간으로 변환 (예: "5분 전")
 */
export function formatRelativeTime(isoString: string): string {
  const date = new Date(isoString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffSec = Math.floor(diffMs / 1000)

  if (diffSec < 60) {
    return '방금 전'
  }

  const diffMin = Math.floor(diffSec / 60)
  if (diffMin < 60) {
    return `${diffMin}분 전`
  }

  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) {
    return `${diffHour}시간 전`
  }

  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) {
    return `${diffDay}일 전`
  }

  const diffMonth = Math.floor(diffDay / 30)
  return `${diffMonth}개월 전`
}
