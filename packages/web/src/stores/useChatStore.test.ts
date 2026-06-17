import { describe, it, expect, beforeEach } from 'vitest'
import { useChatStore } from './useChatStore'
import type { SseEvent } from '@/types/chat'

describe('useChatStore - handleSseEvent', () => {
  beforeEach(() => {
    useChatStore.setState({
      sessions: [],
      activeSessionId: null,
      messages: [],
      isLoading: true,
      toolCalls: [],
      eventSource: null,
      recipeAbortController: null,
    })
  })

  describe('message 이벤트', () => {
    it('messages에 ASSISTANT 역할 메시지를 추가한다', () => {
      const event: SseEvent = {
        type: 'message',
        content: 'AI 응답 메시지입니다.',
      }

      useChatStore.getState().handleSseEvent(event)

      const { messages } = useChatStore.getState()
      expect(messages).toHaveLength(1)
      expect(messages[0].role).toBe('ASSISTANT')
      expect(messages[0].content).toBe('AI 응답 메시지입니다.')
      expect(messages[0].toolCallId).toBeNull()
    })

    it('기존 메시지 뒤에 새 메시지를 추가한다', () => {
      useChatStore.setState({
        messages: [
          { id: 1, role: 'USER', content: '안녕', toolCallId: null, createdAt: '2024-01-01' },
        ],
      })

      const event: SseEvent = { type: 'message', content: '안녕하세요!' }
      useChatStore.getState().handleSseEvent(event)

      const { messages } = useChatStore.getState()
      expect(messages).toHaveLength(2)
      expect(messages[1].role).toBe('ASSISTANT')
      expect(messages[1].content).toBe('안녕하세요!')
    })
  })

  describe('tool_call_start 이벤트', () => {
    it('toolCalls에 active 상태 항목을 추가한다', () => {
      const event: SseEvent = {
        type: 'tool_call_start',
        id: 'tc-001',
        name: '회원 생성',
        subdomain: 'user-service',
        method: 'POST',
        path: '/api/members',
      }

      useChatStore.getState().handleSseEvent(event)

      const { toolCalls } = useChatStore.getState()
      expect(toolCalls).toHaveLength(1)
      expect(toolCalls[0]).toEqual({
        id: 'tc-001',
        name: '회원 생성',
        subdomain: 'user-service',
        method: 'POST',
        path: '/api/members',
        status: 'active',
      })
    })

    it('기존 toolCalls 뒤에 새 항목을 추가한다', () => {
      useChatStore.setState({
        toolCalls: [
          { id: 'tc-000', name: '기존', subdomain: 'svc', method: 'GET', path: '/', status: 'done' },
        ],
      })

      const event: SseEvent = {
        type: 'tool_call_start',
        id: 'tc-001',
        name: '이력서 생성',
        subdomain: 'resume-service',
        method: 'POST',
        path: '/api/resumes',
      }

      useChatStore.getState().handleSseEvent(event)

      const { toolCalls } = useChatStore.getState()
      expect(toolCalls).toHaveLength(2)
      expect(toolCalls[1].status).toBe('active')
    })
  })

  describe('tool_call_result 이벤트', () => {
    it('해당 id의 toolCall을 done 상태로 변경한다', () => {
      useChatStore.setState({
        toolCalls: [
          { id: 'tc-001', name: '회원 생성', subdomain: 'user-service', method: 'POST', path: '/api/members', status: 'active' },
        ],
      })

      const event: SseEvent = {
        type: 'tool_call_result',
        id: 'tc-001',
        result: '{"id": 456}',
      }

      useChatStore.getState().handleSseEvent(event)

      const { toolCalls } = useChatStore.getState()
      expect(toolCalls[0].status).toBe('done')
      expect(toolCalls[0].result).toBe('{"id": 456}')
    })

    it('다른 id의 toolCall은 변경하지 않는다', () => {
      useChatStore.setState({
        toolCalls: [
          { id: 'tc-001', name: '회원 생성', subdomain: 'user-service', method: 'POST', path: '/api/members', status: 'active' },
          { id: 'tc-002', name: '이력서 생성', subdomain: 'resume-service', method: 'POST', path: '/api/resumes', status: 'active' },
        ],
      })

      const event: SseEvent = {
        type: 'tool_call_result',
        id: 'tc-001',
        result: '완료',
      }

      useChatStore.getState().handleSseEvent(event)

      const { toolCalls } = useChatStore.getState()
      expect(toolCalls[0].status).toBe('done')
      expect(toolCalls[1].status).toBe('active')
    })
  })

  describe('done 이벤트', () => {
    it('isLoading을 false로 변경한다', () => {
      useChatStore.setState({ isLoading: true })

      const event: SseEvent = { type: 'done' }
      useChatStore.getState().handleSseEvent(event)

      expect(useChatStore.getState().isLoading).toBe(false)
    })
  })

  describe('error 이벤트', () => {
    it('에러 메시지를 messages에 추가한다', () => {
      const event: SseEvent = {
        type: 'error',
        message: '서버 에러가 발생했습니다.',
      }

      useChatStore.getState().handleSseEvent(event)

      const { messages } = useChatStore.getState()
      expect(messages).toHaveLength(1)
      expect(messages[0].role).toBe('ASSISTANT')
      expect(messages[0].content).toContain('서버 에러가 발생했습니다.')
    })

    it('active 상태인 toolCall을 error로 변경한다', () => {
      useChatStore.setState({
        toolCalls: [
          { id: 'tc-001', name: '완료된 호출', subdomain: 'svc', method: 'GET', path: '/', status: 'done' },
          { id: 'tc-002', name: '진행 중 호출', subdomain: 'svc', method: 'POST', path: '/create', status: 'active' },
        ],
      })

      const event: SseEvent = {
        type: 'error',
        message: '타임아웃',
      }

      useChatStore.getState().handleSseEvent(event)

      const { toolCalls } = useChatStore.getState()
      expect(toolCalls[0].status).toBe('done')
      expect(toolCalls[1].status).toBe('error')
    })

    it('isLoading을 false로 변경한다', () => {
      useChatStore.setState({ isLoading: true })

      const event: SseEvent = {
        type: 'error',
        message: '에러 발생',
      }

      useChatStore.getState().handleSseEvent(event)

      expect(useChatStore.getState().isLoading).toBe(false)
    })
  })
})
