import { useAuthStore } from '@/stores/useAuthStore'
import type { RecipeResponse, UpdateRecipeRequest } from '@/types/recipe'
import type { SseEvent } from '@/types/chat'

const API_BASE = '/api/v1'

function getAuthHeaders(): HeadersInit {
  const token = useAuthStore.getState().token
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export async function getRecipes(): Promise<RecipeResponse[]> {
  const res = await fetch(`${API_BASE}/recipes`, {
    method: 'GET',
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    throw new Error('레시피 목록을 불러오는데 실패했습니다.')
  }

  return res.json()
}

export async function deleteRecipe(id: number): Promise<void> {
  const res = await fetch(`${API_BASE}/recipes/${id}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    throw new Error('레시피 삭제에 실패했습니다.')
  }
}

export async function updateRecipe(
  id: number,
  data: UpdateRecipeRequest,
): Promise<RecipeResponse> {
  const res = await fetch(`${API_BASE}/recipes/${id}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw new Error('레시피 수정에 실패했습니다.')
  }

  return res.json()
}

/**
 * 레시피 실행 SSE 스트림 연결.
 * POST /api/v1/recipes/{id}/execute 가 text/event-stream을 반환하므로
 * fetch + ReadableStream으로 SSE를 수동 파싱한다.
 */
export async function executeRecipeStream(
  recipeId: number,
  variables: Record<string, string>,
  callbacks: {
    onEvent: (event: SseEvent) => void
    onDone: () => void
    onError: (error: Error) => void
  }
): Promise<AbortController> {
  const abortController = new AbortController()

  const res = await fetch(`${API_BASE}/recipes/${recipeId}/execute?sessionId=0`, {
    method: 'POST',
    headers: {
      ...getAuthHeaders(),
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ variables }),
    signal: abortController.signal,
  })

  if (!res.ok) {
    callbacks.onError(new Error(`레시피 실행 요청 실패 (${res.status})`))
    return abortController
  }

  const reader = res.body?.getReader()
  if (!reader) {
    callbacks.onError(new Error('응답 스트림을 읽을 수 없습니다.'))
    return abortController
  }

  // 백그라운드에서 스트림 파싱
  parseSseStream(reader, callbacks)

  return abortController
}

/**
 * 레시피 step 결과 전달.
 * POST /api/v1/recipes/{id}/step-result?sessionId=0&toolCallId=xxx
 */
export async function sendRecipeStepResult(
  recipeId: number,
  toolCallId: string,
  result: string
): Promise<void> {
  const res = await fetch(
    `${API_BASE}/recipes/${recipeId}/step-result?sessionId=0&toolCallId=${encodeURIComponent(toolCallId)}`,
    {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(result),
    }
  )

  if (!res.ok) {
    throw new Error('레시피 step 결과 전달에 실패했습니다.')
  }
}

/** SSE text/event-stream을 ReadableStream에서 라인 단위로 파싱 */
async function parseSseStream(
  reader: ReadableStreamDefaultReader<Uint8Array>,
  callbacks: {
    onEvent: (event: SseEvent) => void
    onDone: () => void
    onError: (error: Error) => void
  }
): Promise<void> {
  const decoder = new TextDecoder()
  let buffer = ''

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        callbacks.onDone()
        break
      }

      buffer += decoder.decode(value, { stream: true })

      // SSE는 빈 줄(\n\n)로 이벤트를 구분
      const events = buffer.split('\n\n')
      // 마지막 요소는 아직 완성되지 않은 이벤트일 수 있음
      buffer = events.pop() ?? ''

      for (const rawEvent of events) {
        if (!rawEvent.trim()) continue

        const dataLine = rawEvent
          .split('\n')
          .find((line) => line.startsWith('data:'))

        if (!dataLine) continue

        const jsonStr = dataLine.slice(5).trim()
        if (!jsonStr) continue

        try {
          const parsed = JSON.parse(jsonStr) as SseEvent
          callbacks.onEvent(parsed)

          if (parsed.type === 'done') {
            callbacks.onDone()
            reader.cancel()
            return
          }
        } catch {
          // JSON 파싱 실패 — 무시
        }
      }
    }
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      // 사용자가 중단한 경우
      return
    }
    callbacks.onError(err instanceof Error ? err : new Error('스트림 읽기 오류'))
  }
}

/**
 * 사용자 요청 텍스트와 유사한 레시피를 검색한다.
 */
export async function suggestRecipes(
  query: string,
  maxResults = 3,
): Promise<RecipeResponse[]> {
  const params = new URLSearchParams({ query, maxResults: String(maxResults) })
  const res = await fetch(`${API_BASE}/recipes/suggest?${params}`, {
    method: 'GET',
    headers: getAuthHeaders(),
  })

  if (!res.ok) return []
  return res.json()
}
