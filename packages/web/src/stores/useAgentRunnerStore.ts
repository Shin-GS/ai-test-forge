import { create } from 'zustand'
import type {
  AgentRunnerStatus,
  PauseReason,
  PauseData,
  ToolCallEvent,
  ToolCallResult,
} from '@/types/agentRunner'

interface AgentRunnerState {
  status: AgentRunnerStatus
  sessionId: string | null
  currentStepIndex: number
  totalSteps: number
  pendingToolCalls: ToolCallEvent[]
  completedResults: ToolCallResult[]
  pauseReason: PauseReason
  pauseData: PauseData | null
  error: string | null
}

interface AgentRunnerActions {
  setStatus: (status: AgentRunnerStatus) => void
  setSessionId: (sessionId: string | null) => void
  setStepProgress: (currentStep: number, totalSteps: number) => void
  addPendingToolCall: (toolCall: ToolCallEvent) => void
  removePendingToolCall: (toolCallId: string) => void
  addCompletedResult: (result: ToolCallResult) => void
  clearResults: () => void
  pause: (reason: PauseReason, data?: PauseData) => void
  resume: () => void
  setError: (error: string | null) => void
  reset: () => void
}

const initialState: AgentRunnerState = {
  status: 'idle',
  sessionId: null,
  currentStepIndex: 0,
  totalSteps: 0,
  pendingToolCalls: [],
  completedResults: [],
  pauseReason: null,
  pauseData: null,
  error: null,
}

export const useAgentRunnerStore = create<AgentRunnerState & AgentRunnerActions>(
  (set) => ({
    ...initialState,

    setStatus: (status) => set({ status }),

    setSessionId: (sessionId) => set({ sessionId }),

    setStepProgress: (currentStep, totalSteps) =>
      set({ currentStepIndex: currentStep, totalSteps }),

    addPendingToolCall: (toolCall) =>
      set((state) => ({
        pendingToolCalls: [...state.pendingToolCalls, toolCall],
      })),

    removePendingToolCall: (toolCallId) =>
      set((state) => ({
        pendingToolCalls: state.pendingToolCalls.filter(
          (tc) => tc.toolCallId !== toolCallId
        ),
      })),

    addCompletedResult: (result) =>
      set((state) => ({
        completedResults: [...state.completedResults, result],
      })),

    clearResults: () => set({ pendingToolCalls: [], completedResults: [] }),

    pause: (reason, data) =>
      set({ status: 'paused', pauseReason: reason, pauseData: data ?? null }),

    resume: () =>
      set({ status: 'running', pauseReason: null, pauseData: null }),

    setError: (error) => set({ error }),

    reset: () => set({ ...initialState }),
  })
)
