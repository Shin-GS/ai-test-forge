import type { SpecResponse, SpecDetailResponse } from '@/types/spec'

const API_BASE = '/api/v1'

export async function getSpecs(): Promise<SpecResponse[]> {
  const token = localStorage.getItem('auth_token')

  const res = await fetch(`${API_BASE}/specs`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!res.ok) {
    throw new Error('서브도메인 목록을 불러오는데 실패했습니다.')
  }

  return res.json()
}

export async function getSpecDetail(
  name: string,
  environment: string,
): Promise<SpecDetailResponse> {
  const token = localStorage.getItem('auth_token')

  const res = await fetch(
    `${API_BASE}/specs/${encodeURIComponent(name)}/detail?environment=${encodeURIComponent(environment)}`,
    {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    },
  )

  if (!res.ok) {
    throw new Error('서브도메인 상세 정보를 불러오는데 실패했습니다.')
  }

  return res.json()
}
