import { useEffect, useCallback, useRef } from 'react'
import { useAgentRunnerStore } from '@/stores/useAgentRunnerStore'
import { useSseConnection } from '@/hooks/useSseConnection'
import { useAuthStore } from '@/stores/useAuthStore'
import type { ToolCallEvent, ToolCallResult } from '@/types/agentRunner'

const API_BASE = '/api/v1'
const ACTIVE_SESSION_KEY = 'activeSessionId'
const TOOL_CALL_TIMEOUT_MS = 30_000

/**
 * Agent Runner 핵심 로직 hook.
 * - 동시 tool_call 병렬 실행 (Promise.allSettled)
 * - AbortController 30초 타임아웃
 * - control 메타데이터에 따른 block/confirm/readonly 처리
 * - 페이지 새로고침 시 세션 복구
 */
export function useAgentRunner() {
  const store = useAgentRunnerStore()
  const { isConnected, reconnect } = useSseConnection(store.sessionId)
  const abortControllersRef = useRef<Map<string, AbortController>>(new Map())

  // 페이지 새로고침 복구
  useEffect(() => {
    const savedSessionId = localStorage.getItem(ACTIVE_SESSION_KEY)
    if (savedSessionId && !store.sessionId) {
      store.setSessionId(savedSessionId)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /**
   * 서브도메인 API를 FE에서 직접 호출.
   * 브라우저 쿠키/세션 자동 부착 (credentials: 'include').
   */
  async function callSubdomainApi(
    toolCall: ToolCallEvent,
    signal: AbortSignal
  ): Promise<ToolCallResult> {
    try {
      const parsedArgs = toolCall.arguments
        ? JSON.parse(toolCall.arguments)
        : undefined

      const fetchOptions: RequestInit = {
        method: toolCall.method,
        credentials: 'include',
        signal,
        headers: {
          'Content-Type': 'application/json',
        },
      }

      // GET, HEAD 요청에는 body 포함하지 않음
      if (
        parsedArgs &&
        toolCall.method !== 'GET' &&
        toolCall.method !== 'HEAD'
      ) {
        fetchOptions.body = JSON.stringify(parsedArgs)
      }

      const response = await fetch(toolCall.path, fetchOptions)

      let body: string | undefined
      try {
        body = await response.text()
      } catch {
        body = undefined
      }

      return {
        toolCallId: toolCall.toolCallId,
        success: response.ok,
        statusCode: response.status,
        body,
      }
    } catch (err) {
      const errorMessage =
        err instanceof DOMException && err.name === 'AbortError'
          ? '요청 시간 초과 (30초)'
          : err instanceof Error
            ? err.message
            : '알 수 없는 오류'

      return {
        toolCallId: toolCall.toolCallId,
        success: false,
        error: errorMessage,
      }
    }
  }

  /**
   * BE에 tool call 결과를 일괄 전달.
   */
  async function sendToolResults(
    sessionId: string,
    results: ToolCallResult[]
  ): Promise<void> {
    const token = useAuthStore.getState().token

    await fetch(`${API_BASE}/chat/${sessionId}/tool-result`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ results }),
    })
  }

  /**
   * 동시 tool_call 병렬 실행.
   * Promise.allSettled()로 일부 실패해도 나머지 결과 전달.
   */
  const executeToolCalls = useCallback(
    async (toolCalls: ToolCallEvent[]) => {
      if (!store.sessionId) return

      const promises = toolCalls.map((toolCall) => {
        const controller = new AbortController()
        abortControllersRef.current.set(toolCall.toolCallId, controller)

        // 30초 타임아웃
        const timeoutId = setTimeout(() => {
          controller.abort()
        }, TOOL_CALL_TIMEOUT_MS)

        return callSubdomainApi(toolCall, controller.signal).finally(() => {
          clearTimeout(timeoutId)
          abortControllersRef.current.delete(toolCall.toolCallId)
        })
      })

      const settled = await Promise.allSettled(promises)

      const results: ToolCallResult[] = settled.map((result, index) => {
        if (result.status === 'fulfilled') {
          return result.value
        }
        // rejected 케이스 (일반적으로 여기 도달하지 않음 — callSubdomainApi가 내부 catch)
        return {
          toolCallId: toolCalls[index].toolCallId,
          success: false,
          error: '실행 중 예기치 못한 오류가 발생했습니다.',
        }
      })

      // 각 결과를 store에 반영
      for (const result of results) {
        store.removePendingToolCall(result.toolCallId)
        store.addCompletedResult(result)
      }

      // BE에 결과 일괄 전달
      await sendToolResults(store.sessionId, results)
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [store.sessionId]
  )

  /**
   * tool_call 수신 시 control 메타데이터 확인 후 적절한 처리.
   *
   * 1. control.blocked → 호출 차단 + 사유 표시 + BE에 실패 전달
   * 2. control.confirm + !control.readonly → pause + 확인 팝업
   * 3. control.readonly → 즉시 실행 (confirm 무시)
   * 4. 일반 → 즉시 실행
   */
  const handleToolCall = useCallback(
    async (toolCall: ToolCallEvent) => {
      if (!store.sessionId) return

      const { control } = toolCall

      // 1. blocked → 차단
      if (control?.blocked) {
        const blockedResult: ToolCallResult = {
          toolCallId: toolCall.toolCallId,
          success: false,
          error: control.blockReason || 'API 호출이 차단되었습니다.',
        }
        store.removePendingToolCall(toolCall.toolCallId)
        store.addCompletedResult(blockedResult)
        await sendToolResults(store.sessionId, [blockedResult])
        return
      }

      // 2. confirm 필요 + readonly가 아닌 경우 → 일시정지
      if (control?.confirmMessage && !control.readonly) {
        store.pause('confirm', { toolCall })
        return
      }

      // 3. readonly 또는 일반 → 즉시 실행
      await executeToolCalls([toolCall])
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [store.sessionId, executeToolCalls]
  )

  /**
   * 사용자가 confirm 다이얼로그에서 [실행]을 선택했을 때.
   */
  const confirmToolCall = useCallback(
    async (toolCall: ToolCallEvent) => {
      store.resume()
      await executeToolCalls([toolCall])
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [executeToolCalls]
  )

  /**
   * 사용자가 confirm 다이얼로그에서 [건너뛰기]를 선택했을 때.
   */
  const rejectToolCall = useCallback(
    async (toolCall: ToolCallEvent) => {
      if (!store.sessionId) return

      store.resume()

      const rejectedResult: ToolCallResult = {
        toolCallId: toolCall.toolCallId,
        success: false,
        error: '사용자가 실행을 거부했습니다.',
      }
      store.removePendingToolCall(toolCall.toolCallId)
      store.addCompletedResult(rejectedResult)
      await sendToolResults(store.sessionId, [rejectedResult])
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [store.sessionId]
  )

  // 언마운트 시 모든 진행 중인 요청 취소
  useEffect(() => {
    const controllers = abortControllersRef.current
    return () => {
      for (const controller of controllers.values()) {
        controller.abort()
      }
      controllers.clear()
    }
  }, [])

  return {
    status: store.status,
    currentStep: store.currentStepIndex,
    totalSteps: store.totalSteps,
    error: store.error,
    pendingToolCalls: store.pendingToolCalls,
    pauseReason: store.pauseReason,
    pauseData: store.pauseData,
    isConnected,
    executeToolCalls,
    handleToolCall,
    confirmToolCall,
    rejectToolCall,
    reconnect,
    pause: store.pause,
    resume: store.resume,
    reset: store.reset,
  }
}
