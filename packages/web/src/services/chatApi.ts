import { useAuthStore } from '@/stores/useAuthStore'
import type { SessionResponse, MessageResponse } from '@/types/chat'

const API_BASE = '/api/v1'

function getAuthHeaders(): HeadersInit {
  const token = useAuthStore.getState().token
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  }
}

export async function createSession(): Promise<SessionResponse> {
  const res = await fetch(`${API_BASE}/chat/sessions`, {
    method: 'POST',
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    throw new Error('세션 생성에 실패했습니다.')
  }

  return res.json()
}

export async function getSessions(): Promise<SessionResponse[]> {
  const res = await fetch(`${API_BASE}/chat/sessions`, {
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    throw new Error('세션 목록을 불러올 수 없습니다.')
  }

  return res.json()
}

export async function sendMessage(
  sessionId: number,
  message: string
): Promise<MessageResponse> {
  const res = await fetch(`${API_BASE}/chat/${sessionId}/messages`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ message }),
  })

  if (!res.ok) {
    throw new Error('메시지 전송에 실패했습니다.')
  }

  return res.json()
}

export async function getMessages(
  sessionId: number
): Promise<MessageResponse[]> {
  const res = await fetch(`${API_BASE}/chat/sessions/${sessionId}/messages`, {
    headers: getAuthHeaders(),
  })

  if (!res.ok) {
    throw new Error('메시지를 불러올 수 없습니다.')
  }

  return res.json()
}

export function connectStream(sessionId: number): EventSource {
  const token = useAuthStore.getState().token
  const url = `${API_BASE}/chat/${sessionId}/stream${token ? `?token=${token}` : ''}`
  return new EventSource(url)
}

export async function sendToolResult(
  sessionId: number,
  data: { toolCallId: string; result: unknown; error?: string }
): Promise<void> {
  const res = await fetch(`${API_BASE}/chat/${sessionId}/tool-result`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(data),
  })

  if (!res.ok) {
    throw new Error('Tool 결과 전달에 실패했습니다.')
  }
}
