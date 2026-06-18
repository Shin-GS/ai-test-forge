import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useAuthGuard } from './useAuthGuard'
import { useAgentRunnerStore } from '@/stores/useAgentRunnerStore'

describe('useAuthGuard', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn())
    useAgentRunnerStore.getState().reset()
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  describe('handleAuthError', () => {
    it('store에 pause("auth")를 설정한다', () => {
      const { result } = renderHook(() => useAuthGuard())

      act(() => {
        result.current.handleAuthError('user-service', 'https://user.dev.company.com/login')
      })

      const state = useAgentRunnerStore.getState()
      expect(state.status).toBe('paused')
      expect(state.pauseReason).toBe('auth')
      expect(state.pauseData).toEqual({
        subdomainName: 'user-service',
        loginUrl: 'https://user.dev.company.com/login',
      })
    })
  })

  describe('checkSubdomainAuth', () => {
    it('200 응답 시 true를 반환한다', async () => {
      const mockFetch = vi.fn().mockResolvedValue({ status: 200 })
      vi.stubGlobal('fetch', mockFetch)

      const { result } = renderHook(() => useAuthGuard())

      // checkAuthStatus를 통해 checkSubdomainAuth 로직 검증
      const authResult = await act(async () => {
        return result.current.checkAuthStatus([
          { name: 'user-service', baseUrl: 'https://user.dev.company.com', loginUrl: 'https://user.dev.company.com/login' },
        ])
      })

      expect(authResult.authenticated).toContain('user-service')
      expect(authResult.unauthenticated).toHaveLength(0)
    })

    it('401 응답 시 false를 반환한다', async () => {
      const mockFetch = vi.fn().mockResolvedValue({ status: 401 })
      vi.stubGlobal('fetch', mockFetch)

      const { result } = renderHook(() => useAuthGuard())

      const authResult = await act(async () => {
        return result.current.checkAuthStatus([
          { name: 'user-service', baseUrl: 'https://user.dev.company.com', loginUrl: 'https://user.dev.company.com/login' },
        ])
      })

      expect(authResult.authenticated).toHaveLength(0)
      expect(authResult.unauthenticated).toEqual([
        { name: 'user-service', loginUrl: 'https://user.dev.company.com/login' },
      ])
    })

    it('네트워크 에러 시 true를 반환한다 (서버 다운과 구분)', async () => {
      const mockFetch = vi.fn().mockRejectedValue(new Error('Network error'))
      vi.stubGlobal('fetch', mockFetch)

      const { result } = renderHook(() => useAuthGuard())

      const authResult = await act(async () => {
        return result.current.checkAuthStatus([
          { name: 'user-service', baseUrl: 'https://user.dev.company.com', loginUrl: 'https://user.dev.company.com/login' },
        ])
      })

      // 네트워크 에러는 인증 실패가 아님 → authenticated로 분류
      expect(authResult.authenticated).toContain('user-service')
      expect(authResult.unauthenticated).toHaveLength(0)
    })
  })

  describe('checkAuthStatus', () => {
    it('여러 서브도메인 동시 확인 결과를 authenticated/unauthenticated로 분류한다', async () => {
      const mockFetch = vi.fn().mockImplementation((url: string) => {
        if (url === 'https://user.dev.company.com') {
          return Promise.resolve({ status: 200 })
        }
        if (url === 'https://payment.dev.company.com') {
          return Promise.resolve({ status: 401 })
        }
        if (url === 'https://recruit.dev.company.com') {
          return Promise.reject(new Error('Network error'))
        }
        return Promise.resolve({ status: 200 })
      })
      vi.stubGlobal('fetch', mockFetch)

      const { result } = renderHook(() => useAuthGuard())

      const authResult = await act(async () => {
        return result.current.checkAuthStatus([
          { name: 'user-service', baseUrl: 'https://user.dev.company.com', loginUrl: 'https://user.dev.company.com/login' },
          { name: 'payment-service', baseUrl: 'https://payment.dev.company.com', loginUrl: 'https://payment.dev.company.com/login' },
          { name: 'recruit-service', baseUrl: 'https://recruit.dev.company.com', loginUrl: 'https://recruit.dev.company.com/login' },
        ])
      })

      // 200 → authenticated
      expect(authResult.authenticated).toContain('user-service')
      // 401 → unauthenticated
      expect(authResult.unauthenticated).toEqual(
        expect.arrayContaining([
          { name: 'payment-service', loginUrl: 'https://payment.dev.company.com/login' },
        ])
      )
      // 네트워크 에러 → authenticated (서버 다운과 구분)
      expect(authResult.authenticated).toContain('recruit-service')
    })
  })
})
