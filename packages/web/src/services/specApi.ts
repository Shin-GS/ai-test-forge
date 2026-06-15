import type { SpecResponse } from '@/types/spec'

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
