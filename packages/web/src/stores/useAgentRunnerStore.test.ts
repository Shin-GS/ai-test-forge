import { describe, it, expect, beforeEach } from 'vitest'
import { useAgentRunnerStore } from './useAgentRunnerStore'
import type { PauseData } from '@/types/agentRunner'

describe('useAgentRunnerStore', () => {
  beforeEach(() => {
    useAgentRunnerStore.getState().reset()
  })

  describe('초기 상태', () => {
    it('status가 idle이다', () => {
      expect(useAgentRunnerStore.getState().status).toBe('idle')
    })

    it('pauseReason이 null이다', () => {
      expect(useAgentRunnerStore.getState().pauseReason).toBeNull()
    })

    it('error가 null이다', () => {
      expect(useAgentRunnerStore.getState().error).toBeNull()
    })

    it('sessionId가 null이다', () => {
      expect(useAgentRunnerStore.getState().sessionId).toBeNull()
    })
  })

  describe('pause()', () => {
    it('status를 paused로 변경한다', () => {
      useAgentRunnerStore.getState().pause('auth')

      expect(useAgentRunnerStore.getState().status).toBe('paused')
    })

    it('pauseReason을 설정한다', () => {
      useAgentRunnerStore.getState().pause('auth')

      expect(useAgentRunnerStore.getState().pauseReason).toBe('auth')
    })

    it('pauseData를 설정한다', () => {
      const data: PauseData = {
        subdomainName: 'user-service',
        loginUrl: 'https://user.dev.company.com/login',
      }

      useAgentRunnerStore.getState().pause('auth', data)

      const state = useAgentRunnerStore.getState()
      expect(state.status).toBe('paused')
      expect(state.pauseReason).toBe('auth')
      expect(state.pauseData).toEqual(data)
    })

    it('confirm 사유로 일시정지할 수 있다', () => {
      const data: PauseData = {
        toolCall: {
          toolCallId: 'tc-001',
          name: '결제',
          subdomain: 'payment-service',
          method: 'POST',
          path: '/api/payments/charge',
          arguments: '{}',
        },
      }

      useAgentRunnerStore.getState().pause('confirm', data)

      const state = useAgentRunnerStore.getState()
      expect(state.status).toBe('paused')
      expect(state.pauseReason).toBe('confirm')
      expect(state.pauseData?.toolCall?.toolCallId).toBe('tc-001')
    })
  })

  describe('resume()', () => {
    it('status를 running으로 변경한다', () => {
      useAgentRunnerStore.getState().pause('auth')
      useAgentRunnerStore.getState().resume()

      expect(useAgentRunnerStore.getState().status).toBe('running')
    })

    it('pauseReason을 null로 초기화한다', () => {
      useAgentRunnerStore.getState().pause('auth')
      useAgentRunnerStore.getState().resume()

      expect(useAgentRunnerStore.getState().pauseReason).toBeNull()
    })

    it('pauseData를 null로 초기화한다', () => {
      const data: PauseData = {
        subdomainName: 'user-service',
        loginUrl: 'https://user.dev.company.com/login',
      }
      useAgentRunnerStore.getState().pause('auth', data)
      useAgentRunnerStore.getState().resume()

      expect(useAgentRunnerStore.getState().pauseData).toBeNull()
    })
  })

  describe('setError()', () => {
    it('error를 설정한다', () => {
      useAgentRunnerStore.getState().setError('네트워크 에러 발생')

      expect(useAgentRunnerStore.getState().error).toBe('네트워크 에러 발생')
    })

    it('null로 에러를 초기화할 수 있다', () => {
      useAgentRunnerStore.getState().setError('에러')
      useAgentRunnerStore.getState().setError(null)

      expect(useAgentRunnerStore.getState().error).toBeNull()
    })
  })
})
