import type { RecipeResponse } from '@/types/recipe'

const API_BASE = '/api/v1'

export async function getRecipes(): Promise<RecipeResponse[]> {
  const token = localStorage.getItem('auth_token')

  const res = await fetch(`${API_BASE}/recipes`, {
    method: 'GET',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!res.ok) {
    throw new Error('레시피 목록을 불러오는데 실패했습니다.')
  }

  return res.json()
}

export async function deleteRecipe(id: number): Promise<void> {
  const token = localStorage.getItem('auth_token')

  const res = await fetch(`${API_BASE}/recipes/${id}`, {
    method: 'DELETE',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })

  if (!res.ok) {
    throw new Error('레시피 삭제에 실패했습니다.')
  }
}
