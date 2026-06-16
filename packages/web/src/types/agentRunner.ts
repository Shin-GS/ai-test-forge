// Agent Runner 관련 타입 정의

/**
 * BE에서 SSE로 전달하는 tool_call 이벤트.
 * FE가 직접 서브도메인 API를 호출해야 하는 지시 정보.
 */
export interface ToolCallEvent {
  toolCallId: string
  name: string
  subdomain: string
  method: string
  path: string
  arguments: string
  control?: ToolControl
}

/**
 * BE가 tool_call에 부착하는 제어 메타데이터.
 * 어노테이션 기반으로 block/confirm/readonly 결정.
 */
export interface ToolControl {
  blocked: boolean
  blockReason?: string
  confirmMessage?: string
  readonly: boolean
  groups: string[]
}

/**
 * FE가 서브도메인 API 호출 후 BE에 전달하는 결과.
 */
export interface ToolCallResult {
  toolCallId: string
  success: boolean
  statusCode?: number
  body?: string
  error?: string
}

/**
 * SSE step_progress 이벤트 데이터.
 */
export interface StepProgressEvent {
  currentStep: number
  totalSteps: number
}

/**
 * SSE recipe_suggestion 이벤트 데이터.
 */
export interface RecipeSuggestionEvent {
  recipeName: string
  description: string
}

/**
 * Agent Runner SSE 이벤트 유니온 타입.
 */
export type AgentRunnerSseEvent =
  | { type: 'tool_call_start'; data: ToolCallEvent }
  | { type: 'tool_call_result'; data: { toolCallId: string; result: string } }
  | { type: 'step_progress'; data: StepProgressEvent }
  | { type: 'done' }
  | { type: 'error'; data: { message: string } }
  | { type: 'recipe_suggestion'; data: RecipeSuggestionEvent }

/**
 * Agent Runner 상태.
 */
export type AgentRunnerStatus = 'idle' | 'running' | 'paused' | 'error'

/**
 * 일시정지 사유.
 */
export type PauseReason = 'auth' | 'confirm' | null

/**
 * 일시정지 시 전달되는 추가 데이터.
 */
export interface PauseData {
  // auth 일시정지
  subdomainName?: string
  loginUrl?: string
  // confirm 일시정지
  toolCall?: ToolCallEvent
}
