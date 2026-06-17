import { useEffect, useRef, useCallback, useState } from 'react'
import { useAuthStore } from '@/stores/useAuthStore'
import { useAgentRunnerStore } from '@/stores/useAgentRunnerStore'
import type { AgentRunnerSseEvent, ToolCallEvent } from '@/types/agentRunner'

const ACTIVE_SESSION_KEY = 'activeSessionId'
const API_BASE = '/api/v1'

// 지수 백오프 설정
const INITIAL_DELAY_MS = 1000
const BACKOFF_MULTIPLIER = 2.0
const MAX_DELAY_MS = 10000
const MAX_RETRY_COUNT = 3

interface UseSseConnectionReturn {
  isConnected: boolean
  lastEventId: string | null
  reconnect: () => void
  disconnect: () => void
}

/**
 * SSE 연결/재연결 관리 hook.
 * - Last-Event-ID 기반 재연결
 * - 지수 백오프 (초기 1초, 배율 2.0, 최대 10초)
 * - 3회 연속 실패 시 status='error' + 수동 재연결 함수 반환
 * - localStorage에 활성 세션 ID 저장/복원
 */
export function useSseConnection(
  sessionId: string | null
): UseSseConnectionReturn {
  const [isConnected, setIsConnected] = useState(false)
  const [lastEventId, setLastEventId] = useState<string | null>(null)

  const eventSourceRef = useRef<EventSource | null>(null)
  const retryCountRef = useRef(0)
  const retryTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const store = useAgentRunnerStore

  const disconnect = useCallback(() => {
    if (retryTimerRef.current) {
      clearTimeout(retryTimerRef.current)
      retryTimerRef.current = null
    }
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }
    setIsConnected(false)
  }, [])

  const handleSseEvent = useCallback((event: AgentRunnerSseEvent) => {
    const state = store.getState()

    switch (event.type) {
      case 'tool_call_start': {
        const toolCallData = event.data as ToolCallEvent
        // control이 JSON 문자열로 올 수 있으므로 파싱
        if (typeof toolCallData.control === 'string') {
          try {
            toolCallData.control = JSON.parse(toolCallData.control as unknown as string)
          } catch {
            toolCallData.control = undefined
          }
        }
        state.addPendingToolCall(toolCallData)
        break
      }

      case 'tool_call_result':
        state.removePendingToolCall(event.data.toolCallId)
        state.addCompletedResult({
          toolCallId: event.data.toolCallId,
          success: true,
          body: event.data.result,
        })
        break

      case 'step_progress':
        state.setStepProgress(event.data.currentStep, event.data.totalSteps)
        break

      case 'done':
        state.setStatus('idle')
        state.clearResults()
        localStorage.removeItem(ACTIVE_SESSION_KEY)
        disconnect()
        break

      case 'error':
        state.setStatus('error')
        state.setError(event.data.message)
        disconnect()
        break

      case 'recipe_suggestion':
        // 향후 UI에서 사용 — 현재는 무시
        break
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disconnect])

  const connect = useCallback(() => {
    if (!sessionId) return

    // 기존 연결 정리
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
      eventSourceRef.current = null
    }

    const token = useAuthStore.getState().token
    const params = new URLSearchParams()
    if (token) params.set('token', token)
    if (lastEventId) params.set('lastEventId', lastEventId)

    const queryString = params.toString()
    const url = `${API_BASE}/chat/${sessionId}/stream${queryString ? `?${queryString}` : ''}`

    const es = new EventSource(url)
    eventSourceRef.current = es

    es.onopen = () => {
      setIsConnected(true)
      retryCountRef.current = 0
      // 세션 ID를 localStorage에 저장
      localStorage.setItem(ACTIVE_SESSION_KEY, sessionId)
      store.getState().setStatus('running')
    }

    es.onmessage = (event) => {
      // Last-Event-ID 추적
      if (event.lastEventId) {
        setLastEventId(event.lastEventId)
      }

      // unnamed event (fallback) — BE가 name 없이 보낸 경우
      try {
        const parsed = JSON.parse(event.data) as AgentRunnerSseEvent
        handleSseEvent(parsed)
      } catch {
        // 파싱 실패 무시
      }
    }

    // named event listeners — Spring SSE의 event.name()으로 전송된 이벤트 처리
    const sseEventTypes = ['tool_call_start', 'tool_call_result', 'step_progress', 'done', 'error', 'message', 'recipe_suggestion', 'next_action_hint'] as const
    for (const eventType of sseEventTypes) {
      es.addEventListener(eventType, (event: MessageEvent) => {
        if (event.lastEventId) {
          setLastEventId(event.lastEventId)
        }
        try {
          const data = JSON.parse(event.data)
          handleSseEvent({ type: eventType, data } as AgentRunnerSseEvent)
        } catch {
          // 파싱 실패 무시
        }
      })
    }

    es.onerror = () => {
      setIsConnected(false)
      es.close()
      eventSourceRef.current = null

      retryCountRef.current += 1

      if (retryCountRef.current >= MAX_RETRY_COUNT) {
        // 3회 연속 실패 → error 상태
        store.getState().setStatus('error')
        store.getState().setError(
          'SSE 연결 실패: 3회 재연결 시도가 모두 실패했습니다. 수동으로 다시 연결해주세요.'
        )
        return
      }

      // 지수 백오프로 재연결
      const delay = Math.min(
        INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, retryCountRef.current - 1),
        MAX_DELAY_MS
      )

      retryTimerRef.current = setTimeout(() => {
        connect()
      }, delay)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId, lastEventId, handleSseEvent, disconnect])

  const reconnect = useCallback(() => {
    retryCountRef.current = 0
    store.getState().setError(null)
    connect()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [connect])

  // sessionId 변경 시 연결/해제
  useEffect(() => {
    if (sessionId) {
      connect()
    } else {
      disconnect()
    }

    return () => {
      disconnect()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId])

  return {
    isConnected,
    lastEventId,
    reconnect,
    disconnect,
  }
}
