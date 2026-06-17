import { useEffect, useRef, useCallback, useState } from 'react'
import { useChatStore } from '@/stores/useChatStore'
import { useAgentRunnerStore } from '@/stores/useAgentRunnerStore'
import { useAgentRunner } from '@/hooks/useAgentRunner'
import { suggestRecipes } from '@/services/recipeApi'
import type { RecipeResponse } from '@/types/recipe'
import SessionSidebar from '@/components/chat/SessionSidebar'
import Onboarding from '@/components/chat/Onboarding'
import MessageBubble from '@/components/chat/MessageBubble'
import ToolCallProgress from '@/components/chat/ToolCallProgress'
import AuthRequiredAlert from '@/components/chat/AuthRequiredAlert'
import ToolCallConfirmDialog from '@/components/chat/ToolCallConfirmDialog'
import ChatInputBar from '@/components/chat/ChatInputBar'
import { Button } from '@/components/ui'

function ChatPage() {
  const sessions = useChatStore((s) => s.sessions)
  const activeSessionId = useChatStore((s) => s.activeSessionId)
  const messages = useChatStore((s) => s.messages)
  const isLoading = useChatStore((s) => s.isLoading)
  const toolCalls = useChatStore((s) => s.toolCalls)
  const fetchSessions = useChatStore((s) => s.fetchSessions)
  const setActiveSession = useChatStore((s) => s.setActiveSession)
  const startNewChat = useChatStore((s) => s.startNewChat)
  const sendUserMessage = useChatStore((s) => s.sendUserMessage)
  const nextActionHints = useChatStore((s) => s.nextActionHints)
  const isRepeatedFailure = useChatStore((s) => s.isRepeatedFailure)
  const isAgentDone = useChatStore((s) => s.isAgentDone)

  // Agent Runner 상태 (인증 필요 감지)
  const pauseReason = useAgentRunnerStore((s) => s.pauseReason)
  const pauseData = useAgentRunnerStore((s) => s.pauseData)
  const { resume, confirmToolCall, rejectToolCall } = useAgentRunner()

  const isAuthRequired = pauseReason === 'auth'
  const isConfirmRequired = pauseReason === 'confirm'

  const messagesEndRef = useRef<HTMLDivElement>(null)

  // 레시피 제안 상태
  const [suggestedRecipes, setSuggestedRecipes] = useState<RecipeResponse[]>([])
  const [pendingMessage, setPendingMessage] = useState<string | null>(null)

  // "더 할 수 있는 것 보기" 로딩 상태
  const [isHintLoading, setIsHintLoading] = useState(false)

  // 초기 세션 목록 로드
  useEffect(() => {
    fetchSessions()
  }, [fetchSessions])

  // 메시지 변경 시 스크롤 하단 추적
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, toolCalls])

  const proceedWithMessage = useCallback(
    (message: string) => {
      setSuggestedRecipes([])
      setPendingMessage(null)
      if (activeSessionId) {
        sendUserMessage(message)
      } else {
        startNewChat(message)
      }
    },
    [activeSessionId, sendUserMessage, startNewChat]
  )

  const handleSend = useCallback(
    async (message: string) => {
      // 레시피 제안 검색 (새 세션에서만)
      if (!activeSessionId) {
        try {
          const suggestions = await suggestRecipes(message, 3)
          if (suggestions.length > 0) {
            setSuggestedRecipes(suggestions)
            setPendingMessage(message)
            return
          }
        } catch {
          // 제안 검색 실패 시 무시하고 바로 전송
        }
      }
      // 제안 없거나 기존 세션이면 바로 전송
      proceedWithMessage(message)
    },
    [activeSessionId, proceedWithMessage]
  )

  const handleUseRecipe = useCallback(
    (recipe: RecipeResponse) => {
      setSuggestedRecipes([])
      setPendingMessage(null)
      useChatStore.getState().executeRecipe(recipe.id, recipe.name, {})
    },
    []
  )

  const handleSkipSuggestion = useCallback(() => {
    if (pendingMessage) {
      proceedWithMessage(pendingMessage)
    }
  }, [pendingMessage, proceedWithMessage])

  const handleNewChat = useCallback(() => {
    useChatStore.setState({
      activeSessionId: null,
      messages: [],
      toolCalls: [],
      isLoading: false,
      nextActionHints: [],
      isRepeatedFailure: false,
      isAgentDone: false,
    })
  }, [])

  const handleSelectSession = useCallback(
    (sessionId: number) => {
      setActiveSession(sessionId)
    },
    [setActiveSession]
  )

  const handleQuickAction = useCallback(
    (message: string) => {
      startNewChat(message)
    },
    [startNewChat]
  )

  // "💡 더 할 수 있는 것 보기" 버튼 클릭
  const handleNextActionHint = useCallback(async () => {
    if (!activeSessionId) return
    setIsHintLoading(true)
    try {
      sendUserMessage('다음으로 할 수 있는 것을 제안해줘')
    } finally {
      setIsHintLoading(false)
    }
  }, [activeSessionId, sendUserMessage])

  // 다음 액션 힌트 버튼 클릭 (SSE로 받은 힌트)
  const handleHintClick = useCallback(
    (hint: string) => {
      sendUserMessage(hint)
    },
    [sendUserMessage]
  )

  // 반복 실패 재시도: 마지막 사용자 메시지 다시 전송
  const handleRetry = useCallback(() => {
    const lastUserMessage = [...messages]
      .reverse()
      .find((msg) => msg.role === 'USER')

    if (lastUserMessage) {
      // 반복 실패 상태 초기화
      useChatStore.setState({ isRepeatedFailure: false })
      sendUserMessage(lastUserMessage.content)
    }
  }, [messages, sendUserMessage])

  // 활성 세션 없음 = 온보딩 표시
  const showOnboarding = !activeSessionId && messages.length === 0

  return (
    <div className="flex flex-1 overflow-hidden">
      {/* 좌측 세션 사이드바 */}
      <SessionSidebar
        sessions={sessions}
        activeSessionId={activeSessionId}
        onSelectSession={handleSelectSession}
        onNewChat={handleNewChat}
      />

      {/* 우측 채팅 영역 */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* 메시지 영역 */}
        <div className="flex-1 overflow-y-auto px-4 py-4">
          {showOnboarding ? (
            <Onboarding onQuickAction={handleQuickAction} />
          ) : (
            <div className="mx-auto max-w-[800px]">
              {messages.map((msg) => (
                <MessageBubble key={msg.id} message={msg} />
              ))}

              {/* Agent Loop 진행 중 또는 완료: tool call progress */}
              {toolCalls.length > 0 && (
                <div className="flex gap-3 px-4 py-3">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-bg-tertiary)] text-sm">
                    🤖
                  </div>
                  <div className="max-w-[70%]">
                    <ToolCallProgress toolCalls={toolCalls} isLoading={isLoading} />
                  </div>
                </div>
              )}

              {/* 인증 필요 Alert */}
              {isAuthRequired && (
                <AuthRequiredAlert pauseData={pauseData} onResume={resume} />
              )}

              {/* Tool Call 확인 다이얼로그 */}
              {isConfirmRequired && pauseData?.toolCall && (
                <ToolCallConfirmDialog
                  toolCall={pauseData.toolCall}
                  onConfirm={() => confirmToolCall(pauseData.toolCall!)}
                  onReject={() => rejectToolCall(pauseData.toolCall!)}
                />
              )}

              {/* 반복 실패 재시도 카드 */}
              {isRepeatedFailure && (
                <div className="mx-auto my-4 max-w-[600px] rounded-lg border border-[var(--color-error)] bg-[var(--color-error-subtle)] p-4">
                  <div className="mb-3 text-sm font-medium text-[var(--color-text-primary)]">
                    ❌ 반복 실패로 중단합니다. 다시 시도하시겠어요?
                  </div>
                  <Button
                    variant="primary"
                    size="sm"
                    onClick={handleRetry}
                  >
                    🔄 재시도
                  </Button>
                </div>
              )}

              {/* Agent Loop 완료 후: "💡 더 할 수 있는 것 보기" 버튼 */}
              {isAgentDone && !isLoading && !isRepeatedFailure && (
                <div className="mx-auto my-4 max-w-[600px]">
                  {/* SSE로 받은 다음 액션 힌트 */}
                  {nextActionHints.length > 0 ? (
                    <div className="rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-4">
                      <div className="mb-3 text-sm font-medium text-[var(--color-text-primary)]">
                        💡 다음으로 할 수 있는 것
                      </div>
                      <div className="flex flex-wrap gap-2">
                        {nextActionHints.map((hint) => (
                          <Button
                            key={hint}
                            variant="ghost"
                            size="sm"
                            onClick={() => handleHintClick(hint)}
                          >
                            {hint}
                          </Button>
                        ))}
                      </div>
                    </div>
                  ) : (
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={handleNextActionHint}
                      disabled={isHintLoading}
                    >
                      {isHintLoading ? '⏳ 로딩 중...' : '💡 더 할 수 있는 것 보기'}
                    </Button>
                  )}
                </div>
              )}

              {/* 레시피 제안 */}
              {suggestedRecipes.length > 0 && (
                <div className="mx-auto my-4 max-w-[600px] rounded-lg border border-[var(--color-border)] bg-[var(--color-bg-secondary)] p-4">
                  <div className="mb-3 text-sm font-medium text-[var(--color-text-primary)]">
                    📋 유사한 레시피가 있습니다
                  </div>
                  <div className="flex flex-col gap-2">
                    {suggestedRecipes.map((recipe) => (
                      <div
                        key={recipe.id}
                        className="flex items-center justify-between rounded-md bg-[var(--color-bg-tertiary)] px-3 py-2"
                      >
                        <div>
                          <div className="text-sm font-medium">{recipe.name}</div>
                          <div className="text-xs text-[var(--color-text-tertiary)]">
                            {recipe.description}
                          </div>
                        </div>
                        <Button
                          variant="primary"
                          size="sm"
                          onClick={() => handleUseRecipe(recipe)}
                        >
                          레시피 실행
                        </Button>
                      </div>
                    ))}
                  </div>
                  <div className="mt-3">
                    <Button variant="ghost" size="sm" onClick={handleSkipSuggestion}>
                      새로 대화로 진행 →
                    </Button>
                  </div>
                </div>
              )}

              {/* 스크롤 앵커 */}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* 입력 바 */}
        <ChatInputBar
          onSend={handleSend}
          isLoading={isLoading}
          disabled={isAuthRequired || isConfirmRequired}
          disabledPlaceholder={
            isAuthRequired
              ? "로그인 후 '계속 진행' 버튼을 눌러주세요"
              : "API 실행 확인 대기 중..."
          }
        />
      </div>
    </div>
  )
}

export default ChatPage
