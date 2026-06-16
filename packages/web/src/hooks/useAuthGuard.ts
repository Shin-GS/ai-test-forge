import { useCallback, useRef, useEffect } from 'react'
import { useAgentRunnerStore } from '@/stores/useAgentRunnerStore'

const storeApi = useAgentRunnerStore

const POLLING_INTERVAL_MS = 10_000 // 10초
const POLLING_TIMEOUT_MS = 5 * 60 * 1000 // 5분

interface AuthCheckResult {
  authenticated: string[]
  unauthenticated: { name: string; loginUrl: string }[]
}

/**
 * 401 감지 → 일시정지 → 폴링으로 로그인 완료 감지 → 재개.
 * 멀티 서비스 사전 인증 체크 기능 포함.
 */
export function useAuthGuard() {
  const pollingIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const pollingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  /**
   * 폴링 자원 정리.
   */
  const cleanupPolling = useCallback(() => {
    if (pollingIntervalRef.current) {
      clearInterval(pollingIntervalRef.current)
      pollingIntervalRef.current = null
    }
    if (pollingTimeoutRef.current) {
      clearTimeout(pollingTimeoutRef.current)
      pollingTimeoutRef.current = null
    }
  }, [])

  /**
   * 서브도메인 인증 상태 확인.
   * 간단한 GET 요청으로 401 여부 판단.
   */
  async function checkSubdomainAuth(baseUrl: string): Promise<boolean> {
    try {
      const response = await fetch(baseUrl, {
        method: 'GET',
        credentials: 'include',
        // 실제 리소스 로드 방지 (health check 용도)
        headers: { Accept: 'application/json' },
      })
      return response.status !== 401
    } catch {
      // 네트워크 에러 시 인증 실패로 간주하지 않음 (서버 다운 등)
      return true
    }
  }

  /**
   * 401 응답 감지 시 호출.
   * Agent Loop를 일시정지하고 10초 간격으로 폴링 시작.
   */
  const handleAuthError = useCallback(
    (subdomainName: string, loginUrl: string) => {
      storeApi.getState().pause('auth', { subdomainName, loginUrl })
      startPolling(subdomainName, loginUrl)
    },
    []
  )

  /**
   * 10초 간격으로 인증 상태를 폴링.
   * 로그인 완료 감지 시 Agent Loop 재개.
   * 5분 타임아웃 초과 시 에러 상태.
   */
  const startPolling = useCallback(
    (subdomainName: string, loginUrl: string) => {
      // 기존 폴링 정리
      cleanupPolling()

      // 서브도메인 base URL 추출 (loginUrl에서 path 제거)
      const url = new URL(loginUrl)
      const baseUrl = url.origin

      pollingIntervalRef.current = setInterval(async () => {
        const isAuthenticated = await checkSubdomainAuth(baseUrl)

        if (isAuthenticated) {
          cleanupPolling()
          storeApi.getState().resume()
        }
      }, POLLING_INTERVAL_MS)

      // 5분 타임아웃
      pollingTimeoutRef.current = setTimeout(() => {
        cleanupPolling()
        const state = storeApi.getState()
        state.setStatus('error')
        state.setError(
          `인증 타임아웃: ${subdomainName}에 5분 이내에 로그인이 완료되지 않았습니다.`
        )
      }, POLLING_TIMEOUT_MS)
    },
    [cleanupPolling]
  )

  /**
   * 멀티 서비스 사전 인증 체크.
   * 여러 서브도메인의 인증 상태를 동시에 확인.
   */
  const checkAuthStatus = useCallback(
    async (
      subdomains: { name: string; baseUrl: string; loginUrl: string }[]
    ): Promise<AuthCheckResult> => {
      const results = await Promise.allSettled(
        subdomains.map(async (sub) => ({
          name: sub.name,
          loginUrl: sub.loginUrl,
          isAuth: await checkSubdomainAuth(sub.baseUrl),
        }))
      )

      const authenticated: string[] = []
      const unauthenticated: { name: string; loginUrl: string }[] = []

      for (const result of results) {
        if (result.status === 'fulfilled') {
          if (result.value.isAuth) {
            authenticated.push(result.value.name)
          } else {
            unauthenticated.push({
              name: result.value.name,
              loginUrl: result.value.loginUrl,
            })
          }
        }
      }

      return { authenticated, unauthenticated }
    },
    []
  )

  // 언마운트 시 폴링 정리
  useEffect(() => {
    return () => {
      cleanupPolling()
    }
  }, [cleanupPolling])

  return {
    handleAuthError,
    checkAuthStatus,
    cleanupPolling,
  }
}
