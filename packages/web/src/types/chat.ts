// 채팅 관련 타입 정의

export interface SessionResponse {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export interface MessageResponse {
  id: number
  sessionId: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  createdAt: string
}

export interface ToolCallItem {
  id: string
  name: string
  subdomain: string
  method: string
  path: string
  status: 'pending' | 'active' | 'done' | 'error'
  result?: string
}

// SSE 이벤트 타입
export interface SseMessageEvent {
  type: 'message'
  content: string
}

export interface SseToolCallStartEvent {
  type: 'tool_call_start'
  id: string
  name: string
  subdomain: string
  method: string
  path: string
  body?: Record<string, unknown>
}

export interface SseToolCallResultEvent {
  type: 'tool_call_result'
  id: string
  result: string
}

export interface SseDoneEvent {
  type: 'done'
}

export interface SseErrorEvent {
  type: 'error'
  message: string
}

export type SseEvent =
  | SseMessageEvent
  | SseToolCallStartEvent
  | SseToolCallResultEvent
  | SseDoneEvent
  | SseErrorEvent
