import { useEffect, useRef, useCallback } from 'react'
import { useChatStore } from '@/stores/useChatStore'
import SessionSidebar from '@/components/chat/SessionSidebar'
import Onboarding from '@/components/chat/Onboarding'
import MessageBubble from '@/components/chat/MessageBubble'
import ToolCallProgress from '@/components/chat/ToolCallProgress'
import ChatInputBar from '@/components/chat/ChatInputBar'

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

  const messagesEndRef = useRef<HTMLDivElement>(null)

  // 초기 세션 목록 로드
  useEffect(() => {
    fetchSessions()
  }, [fetchSessions])

  // 메시지 변경 시 스크롤 하단 추적
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, toolCalls])

  const handleSend = useCallback(
    (message: string) => {
      if (activeSessionId) {
        sendUserMessage(message)
      } else {
        startNewChat(message)
      }
    },
    [activeSessionId, sendUserMessage, startNewChat]
  )

  const handleNewChat = useCallback(() => {
    useChatStore.setState({
      activeSessionId: null,
      messages: [],
      toolCalls: [],
      isLoading: false,
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

              {/* Agent Loop 진행 중: tool call progress */}
              {isLoading && toolCalls.length > 0 && (
                <div className="flex gap-3 px-4 py-3">
                  <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-bg-tertiary)] text-sm">
                    🤖
                  </div>
                  <div className="max-w-[70%]">
                    <ToolCallProgress toolCalls={toolCalls} />
                  </div>
                </div>
              )}

              {/* 스크롤 앵커 */}
              <div ref={messagesEndRef} />
            </div>
          )}
        </div>

        {/* 입력 바 */}
        <ChatInputBar onSend={handleSend} isLoading={isLoading} />
      </div>
    </div>
  )
}

export default ChatPage
