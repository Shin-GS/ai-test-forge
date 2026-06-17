import { create } from 'zustand'
import type {
  SessionResponse,
  MessageResponse,
  ToolCallItem,
  SseEvent,
} from '@/types/chat'
import {
  createSession,
  getSessions,
  getMessages,
  sendMessage,
  connectStream,
} from '@/services/chatApi'
import { executeRecipeStream } from '@/services/recipeApi'

interface ChatState {
  // 세션
  sessions: SessionResponse[]
  activeSessionId: number | null

  // 메시지
  messages: MessageResponse[]

  // Agent Loop 상태
  isLoading: boolean
  toolCalls: ToolCallItem[]

  // SSE 연결
  eventSource: EventSource | null

  // 레시피 실행
  recipeAbortController: AbortController | null

  // 다음 액션 힌트
  nextActionHints: string[]

  // 반복 실패 감지
  isRepeatedFailure: boolean

  // Agent Loop 완료 여부
  isAgentDone: boolean

  // 액션
  fetchSessions: () => Promise<void>
  setActiveSession: (sessionId: number) => Promise<void>
  startNewChat: (message: string) => Promise<void>
  sendUserMessage: (message: string) => Promise<void>
  executeRecipe: (recipeId: number, recipeName: string, variables: Record<string, string>) => Promise<void>
  handleSseEvent: (event: SseEvent) => void
  disconnectStream: () => void
  reset: () => void
}

export const useChatStore = create<ChatState>((set, get) => ({
  sessions: [],
  activeSessionId: null,
  messages: [],
  isLoading: false,
  toolCalls: [],
  eventSource: null,
  recipeAbortController: null,
  nextActionHints: [],
  isRepeatedFailure: false,
  isAgentDone: false,

  fetchSessions: async () => {
    try {
      const sessions = await getSessions()
      set({ sessions })
    } catch {
      // 세션 목록 로딩 실패 시 무시 (빈 목록 유지)
    }
  },

  setActiveSession: async (sessionId: number) => {
    set({ activeSessionId: sessionId, messages: [], toolCalls: [] })
    try {
      const messages = await getMessages(sessionId)
      set({ messages })
    } catch {
      // 메시지 로딩 실패 시 빈 목록
    }
  },

  startNewChat: async (message: string) => {
    set({ isLoading: true, toolCalls: [], nextActionHints: [], isRepeatedFailure: false, isAgentDone: false })
    try {
      const session = await createSession()
      const sessions = get().sessions
      set({
        sessions: [session, ...sessions],
        activeSessionId: session.id,
        messages: [],
      })

      // 사용자 메시지를 로컬에 추가
      const userMsg: MessageResponse = {
        id: Date.now(),
        role: 'USER',
        content: message,
        toolCallId: null,
        createdAt: new Date().toISOString(),
      }
      set((state) => ({
        messages: [...state.messages, userMsg],
      }))

      // 메시지 전송 (BE는 202 빈 body 반환)
      await sendMessage(session.id, message)

      // SSE 연결
      get().disconnectStream()
      const es = connectStream(session.id)
      set({ eventSource: es })

      es.onmessage = (e) => {
        try {
          const data = JSON.parse(e.data) as SseEvent
          get().handleSseEvent(data)
        } catch {
          // 파싱 실패 무시
        }
      }

      es.onerror = () => {
        get().disconnectStream()
        set({ isLoading: false })
      }
    } catch {
      set({ isLoading: false })
    }
  },

  sendUserMessage: async (message: string) => {
    const { activeSessionId } = get()
    if (!activeSessionId) return

    set({ isLoading: true, toolCalls: [], nextActionHints: [], isRepeatedFailure: false, isAgentDone: false })
    try {
      // 사용자 메시지를 로컬에 추가
      const userMsg: MessageResponse = {
        id: Date.now(),
        role: 'USER',
        content: message,
        toolCallId: null,
        createdAt: new Date().toISOString(),
      }
      set((state) => ({
        messages: [...state.messages, userMsg],
      }))

      // 메시지 전송 (BE는 202 빈 body 반환)
      await sendMessage(activeSessionId, message)

      // SSE 연결
      get().disconnectStream()
      const es = connectStream(activeSessionId)
      set({ eventSource: es })

      es.onmessage = (e) => {
        try {
          const data = JSON.parse(e.data) as SseEvent
          get().handleSseEvent(data)
        } catch {
          // 파싱 실패 무시
        }
      }

      es.onerror = () => {
        get().disconnectStream()
        set({ isLoading: false })
      }
    } catch {
      set({ isLoading: false })
    }
  },

  executeRecipe: async (recipeId: number, recipeName: string, variables: Record<string, string>) => {
    // 기존 연결 정리
    get().disconnectStream()
    const { recipeAbortController } = get()
    if (recipeAbortController) {
      recipeAbortController.abort()
    }

    // 채팅 화면으로 전환: 세션 해제 + 시스템 메시지 표시
    const systemMsg: MessageResponse = {
      id: Date.now(),
      role: 'SYSTEM',
      content: `📋 레시피 '${recipeName}' 실행`,
      toolCallId: null,
      createdAt: new Date().toISOString(),
    }

    set({
      activeSessionId: null,
      messages: [systemMsg],
      isLoading: true,
      toolCalls: [],
      recipeAbortController: null,
    })

    try {
      const controller = await executeRecipeStream(recipeId, variables, {
        onEvent: (event) => {
          get().handleSseEvent(event)
        },
        onDone: () => {
          set({ isLoading: false, recipeAbortController: null })
        },
        onError: (error) => {
          const errorMsg: MessageResponse = {
            id: Date.now(),
            role: 'ASSISTANT',
            content: `❌ 레시피 실행 실패: ${error.message}`,
            toolCallId: null,
            createdAt: new Date().toISOString(),
          }
          set((state) => ({
            messages: [...state.messages, errorMsg],
            isLoading: false,
            recipeAbortController: null,
          }))
        },
      })
      set({ recipeAbortController: controller })
    } catch {
      set({ isLoading: false, recipeAbortController: null })
    }
  },

  handleSseEvent: (event: SseEvent) => {
    switch (event.type) {
      case 'message': {
        const aiMessage: MessageResponse = {
          id: Date.now(),
          role: 'ASSISTANT',
          content: event.content,
          toolCallId: null,
          createdAt: new Date().toISOString(),
        }
        set((state) => ({
          messages: [...state.messages, aiMessage],
        }))
        break
      }

      case 'tool_call_start': {
        const newToolCall: ToolCallItem = {
          id: event.id,
          name: event.name,
          subdomain: event.subdomain,
          method: event.method,
          path: event.path,
          status: 'active',
          arguments: event.body ? JSON.stringify(event.body, null, 2) : undefined,
        }
        set((state) => ({
          toolCalls: [...state.toolCalls, newToolCall],
        }))
        break
      }

      case 'tool_call_result': {
        set((state) => ({
          toolCalls: state.toolCalls.map((tc) =>
            tc.id === event.id
              ? { ...tc, status: 'done' as const, result: event.result, responseBody: event.result }
              : tc
          ),
        }))
        break
      }

      case 'done': {
        get().disconnectStream()
        set({ isLoading: false, isAgentDone: true })
        break
      }

      case 'error': {
        // active 상태인 toolCall을 error로 변경
        const updatedToolCalls = get().toolCalls.map((tc) =>
          tc.status === 'active' ? { ...tc, status: 'error' as const } : tc
        )

        // 반복 실패 감지
        const isRepeated = event.message.includes('반복 실패')

        // 에러 메시지를 AI 역할로 추가
        const errorMessage: MessageResponse = {
          id: Date.now(),
          role: 'ASSISTANT',
          content: isRepeated
            ? '❌ 반복 실패로 중단합니다. 다시 시도하시겠어요?'
            : `❌ ${event.message}`,
          toolCallId: null,
          createdAt: new Date().toISOString(),
        }

        get().disconnectStream()
        set((state) => ({
          messages: [...state.messages, errorMessage],
          toolCalls: updatedToolCalls,
          isLoading: false,
          isRepeatedFailure: isRepeated,
        }))
        break
      }

      case 'next_action_hint': {
        // BE는 content로 줄바꿈 구분된 문자열을 보냄 → 라인 단위로 분리하여 배열 변환
        const rawContent = event.content ?? ''
        const hints = rawContent
          .split('\n')
          .map((line: string) => line.replace(/^[-•*\d.)\s]+/, '').trim())
          .filter((line: string) => line.length > 0)
        set({ nextActionHints: hints })
        break
      }
    }
  },

  disconnectStream: () => {
    const { eventSource, recipeAbortController } = get()
    if (eventSource) {
      eventSource.close()
      set({ eventSource: null })
    }
    if (recipeAbortController) {
      recipeAbortController.abort()
      set({ recipeAbortController: null })
    }
  },

  reset: () => {
    get().disconnectStream()
    set({
      sessions: [],
      activeSessionId: null,
      messages: [],
      isLoading: false,
      toolCalls: [],
      recipeAbortController: null,
      nextActionHints: [],
      isRepeatedFailure: false,
      isAgentDone: false,
    })
  },
}))
